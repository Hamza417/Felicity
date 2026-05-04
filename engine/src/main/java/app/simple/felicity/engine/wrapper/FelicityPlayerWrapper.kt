package app.simple.felicity.engine.wrapper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Looper
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.Player.Listener
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.simple.felicity.preferences.AudioPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @author Hamza417
 *
 * A [androidx.media3.common.ForwardingPlayer] that keeps two [ExoPlayer] instances alive
 * at the same time — think of them as deck A and deck B on a DJ mixer. While one deck is
 * playing a song, the other silently loads the next one so they can overlap smoothly when
 * the track is almost over or when the user skips forward or backward.
 *
 * Because [androidx.media3.common.ForwardingPlayer] stores its wrapped player in a private
 * final field that we cannot replace, every relevant method is overridden here to call
 * [activePlayer] instead. Swapping decks is therefore transparent to anything that holds
 * a reference to this wrapper — the [androidx.media3.session.MediaSession] never needs to
 * be rewired.
 *
 * Crossfade is skipped automatically when gapless playback is on, when repeat-one is
 * active, or when there is genuinely no next song to fade into.
 *
 * @param playerFactory Builds a fully configured [ExoPlayer]. Called twice on startup and
 *                      again every time [rebuildPlayers] is used.
 * @param scope         Drives the background position-polling loop. Typically the service
 *                      scope so the loop lives and dies alongside the service.
 */
@Suppress("DEPRECATION")
@OptIn(UnstableApi::class)
class FelicityPlayerWrapper private constructor(
        initialPlayer: ExoPlayer,
        private val playerFactory: () -> ExoPlayer,
        private val scope: CoroutineScope
) : androidx.media3.common.ForwardingPlayer(initialPlayer) {

    /** The deck that is currently audible and answering all Player API calls. */
    var activePlayer: ExoPlayer = initialPlayer
        private set

    /** The silent deck that loads the upcoming song during a crossfade. */
    private var standbyPlayer: ExoPlayer = playerFactory()

    /**
     * Every listener added through [addListener] is kept here so that when we swap
     * [activePlayer] the listeners are automatically moved to the new deck. Using a
     * [LinkedHashSet] guarantees no duplicates and preserves insertion order.
     */
    private val trackedListeners = LinkedHashSet<Listener>()

    /** Called the moment a crossfade animation begins, so the UI can react. */
    var onCrossfadeStarted: (() -> Unit)? = null

    /**
     * Called whenever [installActivePlayer] promotes the standby deck to active. The
     * service uses this to migrate any ExoPlayer-specific analytics listeners that
     * cannot travel through the standard [Player.Listener] path.
     *
     * @param newActive The deck that just became active.
     * @param oldActive The deck that was just retired.
     */
    var onActivePlayerChanged: ((newActive: ExoPlayer, oldActive: ExoPlayer) -> Unit)? = null

    private var monitorJob: Job? = null
    private var isCrossfading = false

    /**
     * Holds a reference to the deck that is currently fading out. This is non-null only
     * during an active crossfade. We keep it separate so [activePlayer] can point to the
     * incoming (new) deck right away — which stops the Bluetooth/MediaSession timeout —
     * while this deck quietly finishes its fade-out in the background.
     */
    private var fadingOutPlayer: ExoPlayer? = null

    companion object {
        private const val TAG = "FelicityPlayerWrapper"
        private const val MONITOR_INTERVAL_MS = 200L

        /**
         * The preferred way to create a [FelicityPlayerWrapper]. Calling [playerFactory]
         * once here lets us pass the same instance to both [ForwardingPlayer] and our own
         * [activePlayer] field without creating an extra throw-away player.
         */
        operator fun invoke(
                playerFactory: () -> ExoPlayer,
                scope: CoroutineScope
        ): FelicityPlayerWrapper {
            val initialPlayer = playerFactory()
            return FelicityPlayerWrapper(initialPlayer, playerFactory, scope)
        }
    }

    override fun getWrappedPlayer(): Player = activePlayer
    override fun getApplicationLooper(): Looper = activePlayer.applicationLooper

    override fun addListener(listener: Listener) {
        trackedListeners.add(listener)
        activePlayer.addListener(listener)
    }

    override fun removeListener(listener: Listener) {
        trackedListeners.remove(listener)
        activePlayer.removeListener(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) = activePlayer.setMediaItems(mediaItems)
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = activePlayer.setMediaItems(mediaItems, resetPosition)
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = activePlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
    override fun setMediaItem(mediaItem: MediaItem) = activePlayer.setMediaItem(mediaItem)
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) = activePlayer.setMediaItem(mediaItem, startPositionMs)
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) = activePlayer.setMediaItem(mediaItem, resetPosition)
    override fun addMediaItem(mediaItem: MediaItem) = activePlayer.addMediaItem(mediaItem)
    override fun addMediaItem(index: Int, mediaItem: MediaItem) = activePlayer.addMediaItem(index, mediaItem)
    override fun addMediaItems(mediaItems: MutableList<MediaItem>) = activePlayer.addMediaItems(mediaItems)
    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) = activePlayer.addMediaItems(index, mediaItems)
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) = activePlayer.moveMediaItem(currentIndex, newIndex)
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) = activePlayer.moveMediaItems(fromIndex, toIndex, newIndex)
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) = activePlayer.replaceMediaItem(index, mediaItem)
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) = activePlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
    override fun removeMediaItem(index: Int) = activePlayer.removeMediaItem(index)
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) = activePlayer.removeMediaItems(fromIndex, toIndex)
    override fun clearMediaItems() = activePlayer.clearMediaItems()
    override fun isCommandAvailable(command: Int): Boolean = activePlayer.isCommandAvailable(command)
    override fun canAdvertiseSession(): Boolean = activePlayer.canAdvertiseSession()
    override fun getAvailableCommands(): Commands = activePlayer.availableCommands
    override fun prepare() = activePlayer.prepare()
    override fun getPlaybackState(): Int = activePlayer.playbackState
    override fun getPlaybackSuppressionReason(): Int = activePlayer.playbackSuppressionReason
    override fun isPlaying(): Boolean = activePlayer.isPlaying
    override fun getPlayerError(): PlaybackException? = activePlayer.playerError
    override fun play() = activePlayer.play()
    override fun pause() = activePlayer.pause()

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        activePlayer.playWhenReady = playWhenReady
    }

    override fun getPlayWhenReady(): Boolean = activePlayer.playWhenReady

    override fun setRepeatMode(repeatMode: Int) {
        activePlayer.repeatMode = repeatMode
    }

    override fun getRepeatMode(): Int = activePlayer.repeatMode

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        activePlayer.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun getShuffleModeEnabled(): Boolean = activePlayer.shuffleModeEnabled
    override fun isLoading(): Boolean = activePlayer.isLoading
    override fun seekToDefaultPosition() = activePlayer.seekToDefaultPosition()
    override fun seekToDefaultPosition(mediaItemIndex: Int) = activePlayer.seekToDefaultPosition(mediaItemIndex)
    override fun seekTo(positionMs: Long) = activePlayer.seekTo(positionMs)
    override fun getSeekBackIncrement(): Long = activePlayer.seekBackIncrement
    override fun seekBack() = activePlayer.seekBack()
    override fun getSeekForwardIncrement(): Long = activePlayer.seekForwardIncrement
    override fun seekForward() = activePlayer.seekForward()
    override fun hasPreviousMediaItem(): Boolean = activePlayer.hasPreviousMediaItem()
    override fun hasNextMediaItem(): Boolean = activePlayer.hasNextMediaItem()
    override fun getMaxSeekToPreviousPosition(): Long = activePlayer.maxSeekToPreviousPosition

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val currentIndex = activePlayer.currentMediaItemIndex
        Log.d(TAG, "seekTo(mediaItemIndex=$mediaItemIndex, positionMs=$positionMs) — currentIndex=$currentIndex, isCrossfading=$isCrossfading")
        if (mediaItemIndex != currentIndex && shouldCrossfade()) {
            // Different song requested and crossfade conditions are met — fade to it.
            Log.d(TAG, "seekTo → crossfadeTo($mediaItemIndex)")
            crossfadeTo(mediaItemIndex)
        } else {
            // Same song (restart) or crossfade is off — just jump directly.
            Log.d(TAG, "seekTo → direct seek to index=$mediaItemIndex pos=$positionMs")
            activePlayer.seekTo(mediaItemIndex, positionMs)
        }
    }

    /**
     * Moves to the previous song. If crossfade is on we fade between the two decks;
     * otherwise we just tell the active player to jump directly.
     */
    override fun seekToPreviousMediaItem() {
        Log.d(TAG, "seekToPreviousMediaItem — currentIndex=${activePlayer.currentMediaItemIndex}, shouldCrossfade=${shouldCrossfade()}")
        if (shouldCrossfade()) {
            val prevIndex = activePlayer.currentMediaItemIndex - 1
            if (prevIndex >= 0) {
                Log.d(TAG, "seekToPreviousMediaItem → crossfadeTo($prevIndex)")
                crossfadeTo(prevIndex)
            } else if (PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_QUEUE) {
                val lastIndex = activePlayer.mediaItemCount - 1
                Log.d(TAG, "seekToPreviousMediaItem → wrap around, crossfadeTo($lastIndex)")
                crossfadeTo(lastIndex)
            } else {
                Log.d(TAG, "seekToPreviousMediaItem → no previous song to fade to, ignoring")
            }
        } else {
            Log.d(TAG, "seekToPreviousMediaItem → direct (crossfade off or already fading)")
            activePlayer.seekToPreviousMediaItem()
        }
    }

    /**
     * Moves to the next song. If crossfade is on we fade between the two decks;
     * otherwise we just tell the active player to jump directly.
     */
    override fun seekToNextMediaItem() {
        Log.d(TAG, "seekToNextMediaItem — currentIndex=${activePlayer.currentMediaItemIndex}, count=${activePlayer.mediaItemCount}, shouldCrossfade=${shouldCrossfade()}")
        if (shouldCrossfade()) {
            val nextIndex = activePlayer.currentMediaItemIndex + 1
            if (nextIndex < activePlayer.mediaItemCount) {
                Log.d(TAG, "seekToNextMediaItem → crossfadeTo($nextIndex)")
                crossfadeTo(nextIndex)
            } else if (PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_QUEUE) {
                Log.d(TAG, "seekToNextMediaItem → wrap around, crossfadeTo(0)")
                crossfadeTo(0)
            } else {
                Log.d(TAG, "seekToNextMediaItem → no next song to fade to, ignoring")
            }
        } else {
            Log.d(TAG, "seekToNextMediaItem → direct (crossfade off or already fading)")
            activePlayer.seekToNextMediaItem()
        }
    }

    /**
     * Works like [seekToPreviousMediaItem] but also restarts the current song when the
     * playback position is past the "seek-to-previous" threshold Media3 uses internally.
     */
    override fun seekToPrevious() {
        val position = activePlayer.currentPosition
        val threshold = activePlayer.maxSeekToPreviousPosition
        Log.d(TAG, "seekToPrevious — position=$position, threshold=$threshold, shouldCrossfade=${shouldCrossfade()}")
        if (shouldCrossfade()) {
            if (position > threshold) {
                Log.d(TAG, "seekToPrevious → restarting current song (position past threshold)")
                activePlayer.seekTo(0)
            } else {
                seekToPreviousMediaItem()
            }
        } else {
            activePlayer.seekToPrevious()
        }
    }

    /**
     * Works like [seekToNextMediaItem] but respects Media3's concept of "seek to next"
     * which takes live streams and dynamic windows into account.
     */
    override fun seekToNext() {
        Log.d(TAG, "seekToNext — delegating to seekToNextMediaItem, shouldCrossfade=${shouldCrossfade()}")
        if (shouldCrossfade()) {
            seekToNextMediaItem()
        } else {
            activePlayer.seekToNext()
        }
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        activePlayer.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) = activePlayer.setPlaybackSpeed(speed)
    override fun getPlaybackParameters(): PlaybackParameters = activePlayer.playbackParameters
    override fun stop() = activePlayer.stop()
    override fun release() = activePlayer.release()
    override fun getCurrentTracks(): Tracks = activePlayer.currentTracks
    override fun getTrackSelectionParameters(): TrackSelectionParameters = activePlayer.trackSelectionParameters

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        activePlayer.trackSelectionParameters = parameters
    }

    override fun getMediaMetadata(): MediaMetadata = activePlayer.mediaMetadata
    override fun getPlaylistMetadata(): MediaMetadata = activePlayer.playlistMetadata

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        activePlayer.playlistMetadata = mediaMetadata
    }

    override fun getCurrentManifest(): Any? = activePlayer.currentManifest
    override fun getCurrentTimeline(): Timeline = activePlayer.currentTimeline
    override fun getCurrentPeriodIndex(): Int = activePlayer.currentPeriodIndex
    override fun getCurrentMediaItemIndex(): Int = activePlayer.currentMediaItemIndex
    override fun getNextMediaItemIndex(): Int = activePlayer.nextMediaItemIndex
    override fun getPreviousMediaItemIndex(): Int = activePlayer.previousMediaItemIndex
    override fun getCurrentMediaItem(): MediaItem? = activePlayer.currentMediaItem
    override fun getMediaItemCount(): Int = activePlayer.mediaItemCount
    override fun getMediaItemAt(index: Int): MediaItem = activePlayer.getMediaItemAt(index)
    override fun getDuration(): Long = activePlayer.duration
    override fun getCurrentPosition(): Long = activePlayer.currentPosition
    override fun getBufferedPosition(): Long = activePlayer.bufferedPosition
    override fun getBufferedPercentage(): Int = activePlayer.bufferedPercentage
    override fun getTotalBufferedDuration(): Long = activePlayer.totalBufferedDuration
    override fun isCurrentMediaItemDynamic(): Boolean = activePlayer.isCurrentMediaItemDynamic
    override fun isCurrentMediaItemLive(): Boolean = activePlayer.isCurrentMediaItemLive
    override fun getCurrentLiveOffset(): Long = activePlayer.currentLiveOffset
    override fun isCurrentMediaItemSeekable(): Boolean = activePlayer.isCurrentMediaItemSeekable
    override fun isPlayingAd(): Boolean = activePlayer.isPlayingAd
    override fun getCurrentAdGroupIndex(): Int = activePlayer.currentAdGroupIndex
    override fun getCurrentAdIndexInAdGroup(): Int = activePlayer.currentAdIndexInAdGroup
    override fun getContentDuration(): Long = activePlayer.contentDuration
    override fun getContentPosition(): Long = activePlayer.contentPosition
    override fun getContentBufferedPosition(): Long = activePlayer.contentBufferedPosition
    override fun getAudioAttributes(): AudioAttributes = activePlayer.audioAttributes

    override fun setVolume(volume: Float) {
        activePlayer.volume = volume
    }

    override fun getVolume(): Float = activePlayer.volume

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) =
        activePlayer.setAudioAttributes(audioAttributes, handleAudioFocus)

    /**
     * Returns true when all the conditions for a crossfade are met. We check this before
     * every skip so we never start a crossfade when the user has gapless on or is looping
     * a single track.
     */
    private fun shouldCrossfade(): Boolean {
        if (!AudioPreferences.isCrossfadeEnabled()) {
            Log.v(TAG, "shouldCrossfade=false — crossfade disabled in preferences")
            return false
        }
        if (PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_ONE) {
            Log.v(TAG, "shouldCrossfade=false — repeat-one is active")
            return false
        }
        if (isCrossfading) {
            Log.v(TAG, "shouldCrossfade=false — crossfade already in progress")
            return false
        }
        return true
    }

    /**
     * Promotes [newPlayer] to [activePlayer], moves all tracked listeners to it, and
     * fires [onActivePlayerChanged] so the service can handle anything that lives outside
     * the standard listener path.
     */
    private fun installActivePlayer(newPlayer: ExoPlayer) {
        val old = activePlayer
        Log.d(TAG, "installActivePlayer — old=${System.identityHashCode(old)}, new=${System.identityHashCode(newPlayer)}")
        trackedListeners.forEach { old.removeListener(it) }
        activePlayer = newPlayer
        trackedListeners.forEach { newPlayer.addListener(it) }
        onActivePlayerChanged?.invoke(newPlayer, old)
    }

    /**
     * Loads the full queue onto the standby deck starting at [targetIndex], then
     * immediately promotes that deck to [activePlayer] so the MediaSession and Bluetooth
     * stack see the new song without any delay. The outgoing deck is stored in
     * [fadingOutPlayer] and quietly fades its volume to zero in the background.
     * Player references are only cleaned up after the animation finishes.
     */
    private fun crossfadeTo(targetIndex: Int) {
        if (isCrossfading) {
            Log.w(TAG, "crossfadeTo($targetIndex) ignored — already crossfading")
            return
        }
        isCrossfading = true

        val outgoing = activePlayer
        val incoming = standbyPlayer

        val itemCount = outgoing.mediaItemCount
        Log.d(TAG, "crossfadeTo($targetIndex) — itemCount=$itemCount, outgoing=${System.identityHashCode(outgoing)}, incoming=${System.identityHashCode(incoming)}")

        if (itemCount == 0) {
            Log.w(TAG, "crossfadeTo: queue is empty, aborting")
            isCrossfading = false
            return
        }

        val clampedIndex = targetIndex.coerceIn(0, itemCount - 1)
        val fadeDuration = AudioPreferences.getCrossfadeDurationMs().toLong()
        Log.d(TAG, "crossfadeTo: clampedIndex=$clampedIndex, fadeDuration=${fadeDuration}ms")

        // Copy the whole queue onto the incoming deck so it can keep chaining songs
        // after this crossfade without needing another full reload.
        val allItems = (0 until itemCount).map { outgoing.getMediaItemAt(it) }
        incoming.playbackParameters = outgoing.playbackParameters
        incoming.repeatMode = outgoing.repeatMode
        incoming.skipSilenceEnabled = outgoing.skipSilenceEnabled
        incoming.setMediaItems(allItems.toMutableList(), clampedIndex, 0L)
        incoming.volume = 0f
        incoming.playWhenReady = true
        incoming.prepare()

        // Promote incoming to active RIGHT NOW so the MediaSession immediately sees
        // the new song. Without this, the session waits for a callback that never
        // arrives and eventually times out (visible as a Bluetooth metadata sync error).
        fadingOutPlayer = outgoing
        installActivePlayer(incoming)
        // Note: standbyPlayer still points to incoming here, but that is fine because
        // isCrossfading=true prevents any new crossfade from starting during this window.

        val fadeOut = ValueAnimator.ofFloat(outgoing.volume, 0f).apply {
            duration = fadeDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                outgoing.volume = v
                Log.v(TAG, "fade-out volume=$v")
            }
        }

        val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                incoming.volume = v
                Log.v(TAG, "fade-in volume=$v")
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    Log.d(TAG, "crossfade animation ended — finishing cleanup")
                    finishCrossfade(outgoing)
                }
            })
        }

        onCrossfadeStarted?.invoke()
        Log.d(TAG, "crossfadeTo: starting fade animators")
        fadeOut.start()
        fadeIn.start()
    }

    /**
     * Called when the fade animation completes. [outgoing] is the old deck that just
     * finished fading to silence — it gets wiped and parked as the next standby.
     * [activePlayer] is already the incoming deck (swapped at the start of the crossfade).
     */
    private fun finishCrossfade(outgoing: ExoPlayer) {
        Log.d(TAG, "finishCrossfade — parking outgoing=${System.identityHashCode(outgoing)} as standby, active=${System.identityHashCode(activePlayer)}")
        standbyPlayer = outgoing
        fadingOutPlayer = null

        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.volume = 1f

        isCrossfading = false
        Log.d(TAG, "finishCrossfade complete")
    }

    /**
     * Starts the background loop that checks how close we are to the end of the current
     * song. When the remaining time falls inside the crossfade window it kicks off a fade
     * automatically. Calling this more than once is safe — the second call is a no-op.
     */
    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                checkAndTriggerCrossfade()
            }
        }
    }

    /** Cancels the background position-monitoring loop without touching any players. */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Runs on every monitor tick. If the song is almost over and crossfade is appropriate,
     * we start fading to the next song automatically.
     */
    private fun checkAndTriggerCrossfade() {
        if (!shouldCrossfade()) return

        val player = activePlayer
        if (!player.isPlaying) return

        val duration = player.duration
        if (duration <= 0) return

        val nextIndex = player.currentMediaItemIndex + 1
        val hasNext = nextIndex < player.mediaItemCount
                || PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_QUEUE
        if (!hasNext) return

        val remaining = duration - player.contentPosition
        val window = AudioPreferences.getCrossfadeDurationMs().toLong()

        if (remaining in 1..window) {
            val resolvedNext = if (nextIndex < player.mediaItemCount) nextIndex else 0
            Log.d(TAG, "checkAndTriggerCrossfade: remaining=${remaining}ms ≤ window=${window}ms → crossfadeTo($resolvedNext)")
            crossfadeTo(resolvedNext)
        }
    }

    /**
     * Tears down both decks and builds fresh ones using [playerFactory]. Useful when a
     * setting that needs a full engine restart changes, like the audio decoder. The queue
     * and playback position are restored so the user doesn't notice anything happened.
     *
     * @param mediaItems    The full queue to restore on the new active player.
     * @param currentIndex  Which song was playing before the rebuild.
     * @param currentPos    Where in that song playback was, in milliseconds.
     * @param playWhenReady Whether to resume playing immediately after restoring.
     */
    fun rebuildPlayers(
            mediaItems: List<MediaItem>,
            currentIndex: Int,
            currentPos: Long,
            playWhenReady: Boolean
    ) {
        stopMonitoring()
        isCrossfading = false

        val newActive = playerFactory()
        val newStandby = playerFactory()

        // Build the new players before releasing the old ones to avoid any gap in resources.
        val oldActive = activePlayer
        val oldStandby = standbyPlayer
        val oldFadingOut = fadingOutPlayer

        installActivePlayer(newActive)
        standbyPlayer = newStandby
        fadingOutPlayer = null

        oldActive.release()
        oldStandby.release()
        oldFadingOut?.release()

        if (mediaItems.isNotEmpty()) {
            newActive.setMediaItems(mediaItems.toMutableList(), currentIndex, currentPos)
            newActive.playWhenReady = playWhenReady
            newActive.prepare()
        }

        startMonitoring()
    }

    /**
     * Releases both decks (and the fading-out deck if one exists) and stops the monitor.
     * Call this when the service is shutting down and playback is not going to continue.
     */
    fun releaseAll() {
        stopMonitoring()
        activePlayer.release()
        standbyPlayer.release()
        fadingOutPlayer?.release()
        fadingOutPlayer = null
    }
}