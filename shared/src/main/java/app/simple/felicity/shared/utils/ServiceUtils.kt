package app.simple.felicity.shared.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object ServiceUtils {
    fun Context.createNotificationAction(icon: Int, title: String, action: String, clazz: Class<*>): NotificationCompat.Action {
        val intent = Intent(this, clazz)
        intent.action = action
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    fun Context.createNotificationChannel(
            channelId: String,
            channelName: String,
            channelDescription: String = "",
            enableVibration: Boolean = false,
            enableLights: Boolean = false
    ) {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = channelDescription
            enableVibration(enableVibration)
            enableLights(enableLights)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun Service.createNotificationChannel(
            channelId: String,
            channelName: String,
            channelDescription: String = "",
            enableVibration: Boolean = false,
            enableLights: Boolean = false
    ) {
        applicationContext.createNotificationChannel(
                channelId,
                channelName,
                channelDescription,
                enableVibration,
                enableLights)
    }
}