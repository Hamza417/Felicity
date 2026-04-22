package app.simple.felicity.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.engine.services.FelicityPlayerService
import app.simple.felicity.repository.database.instances.AudioDatabase
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles button taps from the home screen widget.
 *
 * When the user taps a control button, we connect to [FelicityPlayerService] using
 * [MediaController.Builder]. The magic here is that building the controller automatically
 * starts the service if it isn't already running — so tapping Play four days after the
 * app was last open will wake everything back up, restore the queue from the database,
 * and start playing. Pretty neat for just tapping a button.
 *
 * We also handle the case where the service was killed while the app is still active.
 * The [MediaController.Builder.buildAsync] call will restart it transparently.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class WidgetActionReceiver : BroadcastReceiver() {

    private val TAG = "WidgetActionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // goAsync() gives us a bit more time to finish our async work before Android
        // recycles the receiver. Without it we'd get a strict-mode violation (or worse,
        // a crash) because MediaController.buildAsync is not instant.
        val pendingResult = goAsync()

        val sessionToken = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, FelicityPlayerService::class.java)
        )

        val controllerFuture = MediaController.Builder(
                context.applicationContext,
                sessionToken
        ).buildAsync()

        Futures.addCallback(controllerFuture, object : FutureCallback<MediaController> {
            override fun onSuccess(controller: MediaController) {
                handleAction(context, controller, action) {
                    // Release the controller a moment after the command was sent so ExoPlayer
                    // has time to process it before we let go of the connection.
                    Handler(Looper.getMainLooper()).postDelayed({
                                                                    controller.release()
                                                                    pendingResult.finish()
                                                                }, 600L)
                }
            }

            override fun onFailure(t: Throwable) {
                Log.e(TAG, "Failed to connect to FelicityPlayerService: ${t.message}")
                pendingResult.finish()
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Dispatches the correct playback command based on [action].
     *
     * For play/pause specifically, we check whether the player's queue is empty.
     * An empty queue means the service was just cold-started from a dead state —
     * in that case we restore the saved queue from the database first, then play.
     *
     * [onDone] is called when the command has been issued so the caller can clean up.
     */
    private fun handleAction(context: Context, controller: MediaController, action: String, onDone: () -> Unit) {
        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (controller.mediaItemCount == 0) {
                    // Queue is empty — the service just started from scratch and doesn't know
                    // what to play yet. Dig up the last session from the database and load it.
                    restoreQueueAndPlay(context, controller, onDone)
                } else {
                    // There is already something loaded — just flip the play/pause state.
                    if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                    onDone()
                }
            }

            ACTION_NEXT -> {
                if (controller.mediaItemCount == 0) {
                    restoreQueueAndPlay(context, controller, onDone)
                } else {
                    controller.seekToNextMediaItem()
                    onDone()
                }
            }

            ACTION_PREV -> {
                if (controller.mediaItemCount == 0) {
                    restoreQueueAndPlay(context, controller, onDone)
                } else {
                    controller.seekToPreviousMediaItem()
                    onDone()
                }
            }

            else -> onDone()
        }
    }

    /**
     * Loads the last saved queue from the database, hands it to [MediaPlaybackManager] so
     * the whole app state is consistent, and starts playback.
     *
     * This is exactly what [BaseActivity.restoreLastSongStateFromDatabase] does for the UI path —
     * we mirror the same logic here for the headless widget path.
     */
    private fun restoreQueueAndPlay(context: Context, controller: MediaController, onDone: () -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AudioDatabase.getInstance(context.applicationContext)
                val playbackState = PlaybackStateManager.fetchPlaybackState(db)
                val savedQueue = PlaybackStateManager.getAudiosFromQueueIDs(db)

                if (!savedQueue.isNullOrEmpty() && playbackState != null) {
                    val restoredIndex = when {
                        playbackState.currentHash == 0L -> {
                            playbackState.index.coerceIn(0, savedQueue.size - 1)
                        }
                        else -> {
                            val byHash = savedQueue.indexOfFirst { it.hash == playbackState.currentHash }
                            if (byHash >= 0) byHash else 0
                        }
                    }

                    withContext(Dispatchers.Main) {
                        // Tell MediaPlaybackManager about the restored queue. This also
                        // calls controller.setMediaItems and controller.play via autoPlay=true.
                        MediaPlaybackManager.setMediaController(controller)
                        MediaPlaybackManager.setSongs(
                                audios = savedQueue,
                                position = restoredIndex,
                                startPositionMs = playbackState.position,
                                autoPlay = true
                        )
                    }
                } else {
                    Log.w(TAG, "No saved queue found — nothing to play from widget.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring queue from database: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "app.simple.felicity.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "app.simple.felicity.ACTION_WIDGET_NEXT"
        const val ACTION_PREV = "app.simple.felicity.ACTION_WIDGET_PREV"
    }
}

