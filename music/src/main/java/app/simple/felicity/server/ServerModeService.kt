package app.simple.felicity.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import app.simple.felicity.R
import app.simple.felicity.preferences.ServerPreferences
import app.simple.felicity.server.ServerModeService.Companion.start
import app.simple.felicity.server.ServerModeService.Companion.stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Foreground service that hosts a local WiFi HTTP music server.
 *
 * Lifecycle:
 *
 * - Start via [start] — creates and starts [MusicHttpServer], then posts a persistent
 *   notification showing the reachable `http://ip:port` address.
 * - Stop via [stop] — sends a stop action which shuts down the HTTP server and removes
 *   the notification.
 *
 * The running state is exposed as a process-wide [StateFlow] through the companion object
 * so that any UI component (e.g. [app.simple.felicity.ui.home.Dashboard]) can observe it
 * without binding to the service.
 *
 * @author Hamza417
 */
class ServerModeService : Service() {

    private var httpServer: MusicHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServerAndSelf()
            else -> startHttpServer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shutdownServer()
        super.onDestroy()
    }

    private fun startHttpServer() {
        if (httpServer?.isAlive == true) return

        val port = ServerPreferences.getServerPort()
        val server = MusicHttpServer(applicationContext, port)

        try {
            server.startServer()
            httpServer = server
            _isRunning.value = true

            val ip = resolveLocalIpAddress() ?: getString(R.string.server_unknown_ip)
            startForeground(NOTIFICATION_ID, buildNotification(ip, port))
            Log.i(TAG, "Server started on http://$ip:$port")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server on port $port", e)
            _isRunning.value = false
            stopSelf()
        }
    }

    private fun stopServerAndSelf() {
        shutdownServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shutdownServer() {
        httpServer?.stop()
        httpServer = null
        _isRunning.value = false
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val url = "http://$ip:$port"

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPending = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ServerModeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(url)
            .setSubText(getString(R.string.server_notification_subtext))
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_close, getString(R.string.server_stop_action), stopPending)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_channel_name),
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.server_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Iterates over all network interfaces and returns the first non-loopback IPv4 address
     * found on a WiFi or Ethernet interface.
     *
     * @return A dotted-decimal IP string, or `null` if none is found.
     */
    private fun resolveLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { it.name.startsWith("wlan") || it.name.startsWith("eth") }
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve local IP address", e)
            null
        }
    }

    companion object {
        private const val TAG = "ServerModeService"
        private const val CHANNEL_ID = "felicity_server_mode"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_STOP = "app.simple.felicity.ACTION_SERVER_STOP"

        private val _isRunning = MutableStateFlow(false)

        /**
         * Observable server running state.
         * Emits `true` while the HTTP server is accepting connections, `false` otherwise.
         */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /**
         * Starts the server foreground service.
         * Safe to call if the service is already running — the server will not restart.
         *
         * @param context Application or activity context.
         */
        fun start(context: Context) {
            val intent = Intent(context, ServerModeService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Stops the server foreground service and shuts down the HTTP server.
         *
         * @param context Application or activity context.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ServerModeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

