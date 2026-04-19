package app.simple.felicity.repository.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import app.simple.felicity.repository.loader.PlaylistDatabaseLoader
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

    @Inject
    lateinit var playlistDatabaseLoader: PlaylistDatabaseLoader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentStartId: Int = -1

    /**
     * This flag is set to true BEFORE we launch a scan coroutine, and cleared when
     * the coroutine finishes. Because [onStartCommand] always runs on the main thread,
     * checking and flipping this flag here is effectively race-free — no two
     * [onStartCommand] calls can overlap. This prevents the situation where two rapid
     * start commands both see the scan as idle (the coroutine from the first command
     * hasn't had a chance to run and flip the scan-running flag yet), both launch coroutines,
     * and the second one's empty finally block calls [stopSelfResult] with a high ID —
     * which kills the service and cancels the real scan that the first coroutine started.
     */
    private var scanCoroutineLaunched = false

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
            runCatching {
                val intent = Intent(context, AudioDatabaseService::class.java).apply {
                    action = ACTION_REFRESH_SCAN
                }

                context.startService(intent)
            }.getOrElse {
                it.printStackTrace()
                Log.i(TAG, "Failed to start AudioDatabaseService for refreshScan: ${it.message}")
                Log.v(TAG, "We can safely ignore this, the user can manually scan if required.")
            }
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

        // Always track the latest startId. The running scan's finally block uses
        // currentStartId (not the captured startId) so it always stops the service
        // with the most recent ID — guaranteeing the service actually stops.
        currentStartId = startId

        when (intent?.action) {
            ACTION_REFRESH_SCAN -> {
                startForcedScan()
            }
            else -> {
                startScanIfIdle()
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Only starts a scan if no scan coroutine is currently running. The key detail
     * here is that [scanCoroutineLaunched] is set to true BEFORE the coroutine is
     * dispatched — not inside it. Since [onStartCommand] runs on the main thread
     * sequentially, a second call will always see the flag as true and bail out,
     * even if the first coroutine hasn't started executing on the IO thread yet.
     * This closes the race window that previously allowed two coroutines to both
     * think the scan slot was free.
     */
    private fun startScanIfIdle() {
        if (scanCoroutineLaunched) {
            Log.w(TAG, "Scan coroutine already launched, ignoring startId=$currentStartId")
            return
        }

        scanCoroutineLaunched = true
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting audio database scan (idle path)…")
                audioDatabaseLoader.processAudioFiles()
                Log.d(TAG, "Audio scan completed — starting M3U playlist scan…")
                playlistDatabaseLoader.processM3uFiles()
                Log.d(TAG, "M3U playlist scan completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during scan", e)
            } finally {
                scanCoroutineLaunched = false
                // Use currentStartId here, not the captured startId from the lambda.
                // If more start commands arrived while the scan was running, currentStartId
                // will be the highest one — which is exactly what stopSelfResult needs to
                // see in order to actually stop the service.
                stopSelfResult(currentStartId)
            }
        }
    }

    /**
     * Cancels any running scan and immediately starts a fresh one. The [scanCoroutineLaunched]
     * flag is set synchronously here (still on the main thread) so any concurrent
     * [startScanIfIdle] call that might sneak in is blocked right away.
     */
    private fun startForcedScan() {
        scanCoroutineLaunched = true
        serviceScope.launch {
            try {
                Log.d(TAG, "Forced refresh: cancelling existing scan and starting fresh…")
                audioDatabaseLoader.cancelAndRestartScan()
                Log.d(TAG, "Audio scan completed — starting M3U playlist scan…")
                playlistDatabaseLoader.processM3uFiles()
                Log.d(TAG, "Forced scan completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during forced scan", e)
            } finally {
                scanCoroutineLaunched = false
                stopSelfResult(currentStartId)
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
        startForcedScan()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed - cleaning up")
        scanCoroutineLaunched = false
        audioDatabaseLoader.cleanup()
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed - cleaning up and stopping service")
        scanCoroutineLaunched = false
        audioDatabaseLoader.cleanup()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    inner class AudioDatabaseBinder : Binder() {
        fun getService(): AudioDatabaseService = this@AudioDatabaseService
    }
}