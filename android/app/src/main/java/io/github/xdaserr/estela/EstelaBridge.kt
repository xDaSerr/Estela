package io.github.xdaserr.estela

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Equivalente Android de la clase Api de app.py: ui/index.html la llama a través de android-bridge.js. */
class EstelaBridge(private val activity: MainActivity, private val webView: WebView) {

    private val executor = Executors.newCachedThreadPool()
    private val motor = ReentrantReadWriteLock()
    @Volatile private var busy = false
    @Volatile private var soloIpv4 = false

    @JavascriptInterface
    fun call(id: Int, method: String, argsJson: String) {
        val args = JSONArray(argsJson)
        executor.execute {
            val result = try {
                handle(method, args)
            } catch (e: Throwable) {
                JSONObject().put("ok", false).put("error", limpiarError(e))
            }
            val json = if (result is String) JSONObject.quote(result) else result.toString()
            activity.runOnUiThread { webView.evaluateJavascript("window.__estelaResolve($id, $json)", null) }
        }
    }

    private fun handle(method: String, args: JSONArray): Any = when (method) {
        "get_folder", "choose_folder" -> CARPETA
        "open_folder" -> openFolder()
        "get_info" -> getInfo(args.getString(0))
        "download" -> download(args.getString(0), args.getString(1))
        "check_update" -> checkUpdate()
        "restart" -> {
            activity.runOnUiThread { activity.recreate() }
            JSONObject().put("ok", true)
        }
        else -> throw IllegalArgumentException("Método desconocido: $method")
    }

    private fun emit(event: String, data: JSONObject = JSONObject()) {
        activity.runOnUiThread {
            webView.evaluateJavascript("window.onBackend(${JSONObject.quote(event)}, $data)", null)
        }
    }

    private fun openFolder(): Any {
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
        return JSONObject.NULL
    }

    /** YouTube a veces bloquea la conexión IPv6 con "confirma que no eres un bot": se reintenta por IPv4. */
    private fun <T> conIpv4SiHaceFalta(accion: (ipv4: Boolean) -> T): T {
        if (soloIpv4) return accion(true)
        return try {
            accion(false)
        } catch (e: Throwable) {
            if (!esBloqueoBot(e)) throw e
            accion(true).also { soloIpv4 = true }
        }
    }

    private fun getInfo(url: String): JSONObject {
        EstelaApp.esperarMotor()
        val info = conIpv4SiHaceFalta { ipv4 ->
            val request = YoutubeDLRequest(url).addOption("--no-playlist")
            if (ipv4) request.addOption("--force-ipv4")
            motor.read { YoutubeDL.getInstance().getInfo(request) }
        }
        return JSONObject()
            .put("ok", true)
            .put("title", info.title)
            .put("channel", info.uploader)
            .put("duration", formatDuration(info.duration))
            .put("thumbnail", info.thumbnail)
            .put("views", info.viewCount?.toLongOrNull())
            .put("license", info.license)
    }

    private fun download(url: String, kind: String): JSONObject {
        if (busy) return JSONObject().put("ok", false).put("error", "Ya hay una descarga en curso")
        busy = true
        // Para avisar cuando termine si la app está en segundo plano (Android 13+ pide permiso)
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.runOnUiThread { activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
        executor.execute { runDownload(url, kind) }
        return JSONObject().put("ok", true)
    }

    private fun runDownload(url: String, kind: String) {
        // yt-dlp descarga en la caché de la app y luego se copia a Descargas/Estela
        val temp = File(activity.cacheDir, "descargas/${System.currentTimeMillis()}").apply { mkdirs() }
        DescargaService.iniciar(activity)
        try {
            EstelaApp.esperarMotor()
            var procesando = false
            var ultimo = -1
            conIpv4SiHaceFalta { ipv4 ->
                val request = crearPeticion(url, kind, temp, ipv4)
                motor.read {
                    YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, eta, line ->
                        if (POSTPROCESO.containsMatchIn(line)) {
                            if (!procesando) emit("processing")
                            procesando = true
                        } else if (!procesando && progress >= 0) {
                            if (progress.toInt() != ultimo) {
                                ultimo = progress.toInt()
                                DescargaService.progreso(activity, ultimo)
                            }
                            emit(
                                "progress",
                                JSONObject()
                                    .put("percent", Math.round(progress * 10) / 10.0)
                                    .put("speed", VELOCIDAD.find(line)?.groupValues?.get(1) ?: "")
                                    .put("eta", if (eta > 0) formatDuration(eta.toInt()) else ""),
                            )
                        }
                    }
                }
            }

            val archivos = temp.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
            archivos.forEach(::guardarEnDescargas)
            emit("done", JSONObject().put("folder", CARPETA))
            if (!MainActivity.enPantalla) {
                DescargaService.avisar(activity, "✨ Descarga lista", "${archivos.joinToString { it.name }} se guardó en $CARPETA")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Falló la descarga de $url ($kind)", e)
            val mensaje = limpiarError(e)
            emit("error", JSONObject().put("error", mensaje))
            if (!MainActivity.enPantalla) DescargaService.avisar(activity, "No se pudo descargar", mensaje)
        } finally {
            DescargaService.detener(activity)
            temp.deleteRecursively()
            busy = false
        }
    }

    private fun crearPeticion(url: String, kind: String, temp: File, ipv4: Boolean): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("-o", "${temp.absolutePath}/%(title)s.%(ext)s")
        if (ipv4) request.addOption("--force-ipv4")
        if (PAGINAS_16K) {
                // El FFmpeg de youtubedl-android no carga con páginas de 16 KB:
                // se eligen formatos que vienen en un solo archivo y no hay que convertir
                when (kind) {
                    "video" -> request.addOption("-f", "b[ext=mp4]/b")
                    "video720" -> request.addOption("-f", "b[height<=720][ext=mp4]/b[height<=720]/b")
                    "audio" -> request.addOption("-f", "ba[ext=m4a]/ba")
                }
            } else when (kind) {
                // Video en H.264 (avc1), que se reproduce en cualquier dispositivo; si no lo hay, lo mejor disponible
                "video" -> request
                    .addOption("-f", "bv*[vcodec^=avc1]+ba[ext=m4a]/b[vcodec^=avc1]/bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    .addOption("--merge-output-format", "mp4")
                "video720" -> request
                    .addOption("-f", "bv*[vcodec^=avc1][height<=720]+ba[ext=m4a]/b[vcodec^=avc1][height<=720]/bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]")
                    .addOption("--merge-output-format", "mp4")
                "audio" -> request
                    .addOption("-f", "ba/b")
                    .addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "192K")
            }
        return request
    }

    /** Copia el archivo a Descargas/Estela mediante MediaStore (no necesita permisos de almacenamiento). */
    private fun guardarEnDescargas(archivo: File) {
        val resolver = activity.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, archivo.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType(archivo.extension))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Estela")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: throw IllegalStateException("No se pudo guardar ${archivo.name}")
        resolver.openOutputStream(uri)!!.use { salida -> archivo.inputStream().use { it.copyTo(salida) } }
        valores.clear()
        valores.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, valores, null, null)
    }

    private fun checkUpdate(): JSONObject {
        EstelaApp.esperarMotor()
        val ytdl = YoutubeDL.getInstance()
        return try {
            // En Android la versión nueva se aplica al momento, sin reiniciar.
            // Mientras se reemplaza, las búsquedas y descargas esperan (y viceversa).
            motor.write { ytdl.updateYoutubeDL(activity.applicationContext, YoutubeDL.UpdateChannel.STABLE) }
            JSONObject().put("status", "ok").put("version", ytdl.version(activity.applicationContext) ?: "incluida")
        } catch (e: Throwable) {
            JSONObject().put("status", "offline").put("version", ytdl.version(activity.applicationContext) ?: "incluida")
        }
    }

    private fun esBloqueoBot(e: Throwable) = e.message?.contains("not a bot") == true

    private fun limpiarError(e: Throwable): String {
        if (esBloqueoBot(e)) return MENSAJE_BOT
        val texto = e.message ?: e.javaClass.simpleName
        // yt-dlp escribe varias líneas; la útil es la que empieza por ERROR:
        // o, si Python falló, la última del traceback (la que dice la causa)
        val lineas = texto.lines().filter { it.isNotBlank() }
        val linea = lineas.lastOrNull { it.startsWith("ERROR:") } ?: lineas.lastOrNull() ?: e.javaClass.simpleName
        return linea.removePrefix("ERROR:").trim()
    }

    companion object {
        private const val TAG = "Estela"
        private const val CARPETA = "Descargas/Estela"
        private const val PROCESS_ID = "descarga"
        private val POSTPROCESO = Regex("\\[(Merger|ExtractAudio|VideoConvertor|FixupM4a)]")
        private val VELOCIDAD = Regex("at\\s+(\\S+/s)")
        private val PAGINAS_16K = Os.sysconf(OsConstants._SC_PAGESIZE) > 4096
        private const val MENSAJE_BOT =
            "YouTube pidió confirmar que no eres un bot 🤖. Espera un rato o cambia de red (por ejemplo, a datos móviles) y vuelve a intentarlo."

        private fun formatDuration(segundos: Int): String {
            if (segundos <= 0) return ""
            val h = segundos / 3600
            val m = segundos % 3600 / 60
            val s = segundos % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        private fun mimeType(extension: String) = when (extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }
}
