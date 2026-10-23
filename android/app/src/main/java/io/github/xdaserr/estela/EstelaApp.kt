package io.github.xdaserr.estela

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Prepara Python, yt-dlp y FFmpeg en segundo plano al abrir la app (la primera vez tarda unos segundos). */
class EstelaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        thread(name = "motor") {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
            } catch (e: Throwable) {
                errorMotor = e
            } finally {
                motorListo.countDown()
            }
        }
    }

    companion object {
        private val motorListo = CountDownLatch(1)
        @Volatile private var errorMotor: Throwable? = null

        /** Espera a que el motor esté listo; lanza el error si no se pudo preparar. */
        fun esperarMotor() {
            motorListo.await()
            errorMotor?.let { throw it }
        }
    }
}
