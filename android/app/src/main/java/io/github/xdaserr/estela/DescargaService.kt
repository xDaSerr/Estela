package io.github.xdaserr.estela

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Servicio en primer plano mientras hay una descarga: sin él, Android corta internet a la app
 * en cuanto pasa a segundo plano y la descarga falla. Muestra el progreso en una notificación.
 */
class DescargaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID_PROGRESO, construir(this, "Preparando la descarga…", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    companion object {
        private const val CANAL_PROGRESO = "descargas"
        private const val CANAL_AVISOS = "avisos"
        private const val ID_PROGRESO = 1
        private const val ID_AVISO = 2

        fun iniciar(context: Context) {
            crearCanales(context)
            context.startForegroundService(Intent(context, DescargaService::class.java))
        }

        fun progreso(context: Context, porcentaje: Int) {
            notificaciones(context).notify(ID_PROGRESO, construir(context, "Descargando… $porcentaje %", porcentaje))
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, DescargaService::class.java))
        }

        /** Aviso final (solo se muestra si Estela no está en pantalla). */
        fun avisar(context: Context, titulo: String, texto: String) {
            val aviso = Notification.Builder(context, CANAL_AVISOS)
                .setSmallIcon(R.drawable.ic_frente)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(Notification.BigTextStyle().bigText(texto))
                .setContentIntent(abrirApp(context))
                .setAutoCancel(true)
                .build()
            notificaciones(context).notify(ID_AVISO, aviso)
        }

        private fun construir(context: Context, texto: String, porcentaje: Int): Notification =
            Notification.Builder(context, CANAL_PROGRESO)
                .setSmallIcon(R.drawable.ic_frente)
                .setContentTitle("Estela")
                .setContentText(texto)
                .setProgress(100, porcentaje.coerceAtLeast(0), porcentaje < 0)
                .setContentIntent(abrirApp(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

        private fun abrirApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun crearCanales(context: Context) {
            notificaciones(context).createNotificationChannels(
                listOf(
                    NotificationChannel(CANAL_PROGRESO, "Descargas en curso", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(CANAL_AVISOS, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT),
                ),
            )
        }

        private fun notificaciones(context: Context) = context.getSystemService(NotificationManager::class.java)
    }
}
