package app.simple.felicity.repository.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioDatabaseService : Service() {

    @Inject
    lateinit var audioDatabaseLoader: AudioDatabaseLoader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentStartId: Int = -1

    companion object {
        private const val TAG = "AudioDatabaseService"
        private const val ACTION_START_SCAN = "app.simple.felicity.ACTION_START_SCAN"
        private const val ACTION_REFRESH_SCAN = "app.simple.felicity.ACTION_REFRESH_SCAN"

        /**
         * Start the audio database scan service (skips if a scan is already running).
         */
        fun startScan(context: Context) {
            val intent = Intent(context, AudioDatabaseService::class.java).apply {
                action = ACTION_START_SCAN
            }
            context.startService(intent)
        }

        /**
         * Cancel any running scan and immediately start a fresh one.
         * Use this on app resume so a zombie scan never blocks the refresh.
         */
        @Keep
        fun refreshScan(context: Context) {
            val intent = Intent(context, AudioDatabaseService::class.java).apply {
                action = ACTION_REFRESH_SCAN
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return AudioDatabaseBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}, startId: $startId")
        currentStartId = startId

        when (intent?.action) {
            ACTION_REFRESH_SCAN -> {
                // Cancel whatever is running and force a fresh scan
                startForcedScan(startId)
            }
            else -> {
                // ACTION_START_SCAN or no action – only run if nothing is in progress
                startScanIfIdle(startId)
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Run a scan only when no scan is already active.
     * Silently drops the request if one is already running.
     */
    private fun startScanIfIdle(startId: Int) {
        if (audioDatabaseLoader.isScanInProgress()) {
            Log.w(TAG, "Scan already in progress, ignoring duplicate start request")
            return
        }
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting audio database scan (idle path)…")
                audioDatabaseLoader.processAudioFiles()
                Log.d(TAG, "Scan completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during scan", e)
            } finally {
                stopSelfResult(startId)
            }
        }
    }

    /**
     * Cancel the current scan (if any) and immediately start a new one.
     * This is the path used on app resume – it is guaranteed to run.
     */
    private fun startForcedScan(startId: Int) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Forced refresh: cancelling existing scan and starting fresh…")
                audioDatabaseLoader.cancelAndRestartScan()
                Log.d(TAG, "Forced scan completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during forced scan", e)
            } finally {
                stopSelfResult(startId)
            }
        }
    }

    /**
     * Check if a scan is currently in progress.
     */
    fun isScanInProgress(): Boolean = audioDatabaseLoader.isScanInProgress()

    /**
     * Force-refresh audio files (for bound clients, e.g. a settings screen).
     */
    fun refreshAudioFiles() {
        Log.d(TAG, "Manual refresh requested via binder")
        startForcedScan(currentStartId)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed - cleaning up")
        audioDatabaseLoader.cleanup()
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed - cleaning up and stopping service")
        audioDatabaseLoader.cleanup()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    inner class AudioDatabaseBinder : Binder() {
        fun getService(): AudioDatabaseService = this@AudioDatabaseService
    }
}