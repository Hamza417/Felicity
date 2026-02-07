package app.simple.felicity.repository.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@AndroidEntryPoint
class AudioDatabaseService : Service() {

    @Inject
    lateinit var audioDatabaseLoader: AudioDatabaseLoader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private var currentStartId: Int = -1

    companion object {
        private const val TAG = "AudioDatabaseService"
        private const val ACTION_START_SCAN = "app.simple.felicity.ACTION_START_SCAN"
        private const val ACTION_REFRESH_SCAN = "app.simple.felicity.ACTION_REFRESH_SCAN"

        /**
         * Start the audio database scan service
         */
        fun startScan(context: Context) {
            val intent = Intent(context, AudioDatabaseService::class.java).apply {
                action = ACTION_START_SCAN
            }
            context.startService(intent)
        }

        /**
         * Refresh/restart the audio database scan
         */
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
        // Never run scan here - wait for onStartCommand
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return AudioDatabaseBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}, startId: $startId")
        currentStartId = startId

        when (intent?.action) {
            ACTION_START_SCAN, ACTION_REFRESH_SCAN -> {
                startScanWithGuard(startId)
            }
            else -> {
                // No action specified, just start a scan
                startScanWithGuard(startId)
            }
        }

        // Use START_NOT_STICKY - don't auto-restart if killed
        // The scan can be safely restarted by the user/system when needed
        return START_NOT_STICKY
    }

    /**
     * Start scan with mutex guard to prevent parallel scans
     */
    private fun startScanWithGuard(startId: Int) {
        serviceScope.launch {
            // Try to acquire mutex - if already locked, skip
            if (!scanMutex.tryLock()) {
                Log.w(TAG, "Scan already in progress, ignoring duplicate request")
                // Still stop this startId since we're not doing work
                checkAndStopService(startId)
                return@launch
            }

            try {
                Log.d(TAG, "Starting audio database scan...")
                performScan()
                Log.d(TAG, "Audio database scan completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio database scan", e)
            } finally {
                scanMutex.unlock()
                // Stop service after work completes
                checkAndStopService(startId)
            }
        }
    }

    /**
     * Perform the actual scan - delegates to AudioDatabaseLoader
     */
    private suspend fun performScan() {
        // The loader has its own internal guards, but we also guard at service level
        audioDatabaseLoader.processAudioFiles()
    }

    /**
     * Stop the service after work is complete
     */
    private fun checkAndStopService(startId: Int) {
        if (!isScanInProgress()) {
            Log.d(TAG, "No scans in progress, stopping service with startId: $startId")
            stopSelfResult(startId)
        }
    }

    /**
     * Check if a scan is currently in progress
     */
    fun isScanInProgress(): Boolean {
        return audioDatabaseLoader.isScanInProgress() || scanMutex.isLocked
    }

    /**
     * Manually refresh audio files (for bound clients)
     * This is safe to call - it won't create duplicate scans
     */
    fun refreshAudioFiles() {
        Log.d(TAG, "Manual refresh requested")
        startScanWithGuard(currentStartId)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed - cleaning up")
        // Cancel all ongoing operations before destroying
        audioDatabaseLoader.cleanup()
        // Cancel the service scope
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed - cleaning up and stopping service")
        // App was swiped away from recent apps
        audioDatabaseLoader.cleanup()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    inner class AudioDatabaseBinder : Binder() {
        fun getService(): AudioDatabaseService = this@AudioDatabaseService
    }
}