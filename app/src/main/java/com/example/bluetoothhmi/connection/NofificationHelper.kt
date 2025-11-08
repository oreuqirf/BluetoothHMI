package com.example.bluetoothhmi.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.bluetoothhmi.R // ¡Asegúrate de tener este icono!

object NotificationHelper {

    private const val CHANNEL_ID = "BluetoothServiceChannel"
    const val NOTIFICATION_ID = 1

    /**
     * Crea el canal de notificación (necesario para Android 8+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_LOW // Usa LOW para que no haga sonido
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Crea la notificación persistente
     */
    fun createNotification(context: Context, statusText: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Servicio Bluetooth HMI")
            .setContentText(statusText)
            // ¡Usa el icono de mipmap, es más seguro que drawable!
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // Hace que sea persistente
            .build()
    }
}

