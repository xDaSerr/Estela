package io.github.xdaserr.estela

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var paginaLista = false
    private var urlCompartida: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Permite inspeccionar la página con chrome://inspect solo en las compilaciones de depuración
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1033"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        val raiz = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1A1033"))
            addView(webView)
        }
        // Deja libre el espacio de la barra de estado y la de navegación
        raiz.setOnApplyWindowInsetsListener { vista, insets ->
            val barras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsets
            }
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(raiz)

        webView.addJavascriptInterface(EstelaBridge(this, webView), "EstelaAndroid")
        val puente = assets.open("android-bridge.js").bufferedReader().use { it.readText() }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(puente, null)
                paginaLista = true
                buscarCompartida()
            }
        }
        urlCompartida = extraerUrl(intent)
        webView.loadUrl("file:///android_asset/ui/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlCompartida = extraerUrl(intent) ?: return
        if (paginaLista) buscarCompartida()
    }

    /** Si se compartió un enlace con Estela, lo pega y lo busca. */
    private fun buscarCompartida() {
        val url = urlCompartida ?: return
        urlCompartida = null
        webView.evaluateJavascript(
            "document.activeElement?.blur(); document.getElementById('url').value = ${JSONObject.quote(url)}; search();",
            null,
        )
    }

    private fun extraerUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S+").find(texto)?.value
    }

    override fun onResume() {
        super.onResume()
        enPantalla = true
    }

    override fun onPause() {
        enPantalla = false
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /** Si Estela está a la vista; si no, el final de una descarga se avisa con una notificación. */
        @Volatile var enPantalla = false
    }
}
