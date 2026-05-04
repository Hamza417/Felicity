package app.simple.felicity.engine.wrapper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
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
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
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
import androidx.media3.common.util.Size as Media3Size

/**
 * @author Hamza417
 *
 * A [androidx.media3.common.ForwardingPlayer] subclass that manages two [ExoPlayer]
 * instances — nicknamed **deck A** and **deck B** — to deliver smooth volume-based
 * crossfades between songs. The concept is borrowed from a DJ mixer: while one deck
 * finishes playing a track, the other silently pre-loads the next song and begins fading
 * it in. Both volumes animate simultaneously so the listener hears a continuous blend.
 *
 * Because this class is a proper [androidx.media3.common.ForwardingPlayer], the
 * [androidx.media3.session.MediaSession] only needs to be wired to the wrapper once
 * at service startup — no player swaps from the service side are required. Every Player
 * method is overridden to route through [activePlayer], so swapping the active deck
 * is invisible to the session and any connected [androidx.media3.session.MediaController].
 *
 * The wrapper also tracks all [Listener]s registered by the session or the service and
 * re-registers them automatically whenever a new active player is installed (either after
 * a crossfade swap or a full [rebuildPlayers] call).
 *
 * **Crossfade is skipped automatically when:**
 * - Gapless playback is on (ExoPlayer handles seamless transitions itself).
 * - The queue is set to repeat a single track (nothing different to fade into).
 * - There is no next item and repeat-all is off.
 *
 * @param playerFactory A lambda that creates a fully configured [ExoPlayer]. Called twice
 *                      at startup and again whenever [rebuildPlayers] is invoked.
 * @param scope         The [CoroutineScope] that drives the background position-monitor.
 *                      Typically the service scope so the loop lives and dies with it.
 */
@Suppress("DEPRECATION")
@OptIn(UnstableApi::class)
class FelicityPlayerWrapper private constructor(
        initialPlayer: ExoPlayer,
        private val playerFactory: () -> ExoPlayer,
        private val scope: CoroutineScope
) : androidx.media3.common.ForwardingPlayer(initialPlayer) {

    /** The player currently producing audio and handling all Player API calls. */
    var activePlayer: ExoPlayer = initialPlayer
        private set

    /** The silent player that pre-loads the next song during a crossfade. */
    private var standbyPlayer: ExoPlayer = playerFactory()

    /**
     * Every [Listener] added via [addListener] is stored here so that when [activePlayer]
     * is replaced — after a crossfade or a full engine rebuild — those listeners can be
     * automatically re-registered on the new active player. A set prevents any single
     * listener from being registered more than once.
     */
    private val trackedListeners = LinkedHashSet<Listener>()

    /** Optional hook called the instant a crossfade animation begins, for UI feedback. */
    var onCrossfadeStarted: (() -> Unit)? = null

    /**
     * Fired whenever [installActivePlayer] replaces [activePlayer] with a new instance.
     * The service uses this to migrate [AnalyticsListener]s, which are ExoPlayer-specific
     * and cannot be tracked through the standard [Player.Listener] path.
     *
     * @param newActive The newly installed active player.
     * @param oldActive The player that was just replaced.
     */
    var onActivePlayerChanged: ((newActive: ExoPlayer, oldActive: ExoPlayer) -> Unit)? = null

    private var monitorJob: Job? = null
    private var isCrossfading = false

    companion object {
        private const val MONITOR_INTERVAL_MS = 200L

        /**
         * Creates a [FelicityPlayerWrapper] using [playerFactory] to build the initial
         * active and standby players. Use this instead of the constructor so the first
         * player can be both passed to [ForwardingPlayer] and stored locally.
         */
        operator fun invoke(
                playerFactory: () -> ExoPlayer,
                scope: CoroutineScope
        ): FelicityPlayerWrapper {
            val initialPlayer = playerFactory()
            return FelicityPlayerWrapper(initialPlayer, playerFactory, scope)
        }
    }

    // The base ForwardingPlayer delegates everything to a private final field that we
    // cannot change. We override every Player method here to route through our own
    // mutable [activePlayer], making the delegation fully dynamic.

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
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = activePlayer.seekTo(mediaItemIndex, positionMs)
    override fun getSeekBackIncrement(): Long = activePlayer.seekBackIncrement
    override fun seekBack() = activePlayer.seekBack()
    override fun getSeekForwardIncrement(): Long = activePlayer.seekForwardIncrement
    override fun seekForward() = activePlayer.seekForward()
    override fun hasPreviousMediaItem(): Boolean = activePlayer.hasPreviousMediaItem()
    override fun hasNextMediaItem(): Boolean = activePlayer.hasNextMediaItem()
    override fun seekToPreviousMediaItem() = activePlayer.seekToPreviousMediaItem()
    override fun seekToNextMediaItem() = activePlayer.seekToNextMediaItem()
    override fun getMaxSeekToPreviousPosition(): Long = activePlayer.maxSeekToPreviousPosition
    override fun seekToPrevious() = activePlayer.seekToPrevious()
    override fun seekToNext() = activePlayer.seekToNext()
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
    override fun clearVideoSurface() = activePlayer.clearVideoSurface()
    override fun clearVideoSurface(surface: Surface?) = activePlayer.clearVideoSurface(surface)
    override fun setVideoSurface(surface: Surface?) = activePlayer.setVideoSurface(surface)
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) = activePlayer.setVideoSurfaceHolder(surfaceHolder)
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) = activePlayer.clearVideoSurfaceHolder(surfaceHolder)
    override fun setVideoSurfaceView(surfaceView: SurfaceView?) = activePlayer.setVideoSurfaceView(surfaceView)
    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) = activePlayer.clearVideoSurfaceView(surfaceView)
    override fun setVideoTextureView(textureView: TextureView?) = activePlayer.setVideoTextureView(textureView)
    override fun clearVideoTextureView(textureView: TextureView?) = activePlayer.clearVideoTextureView(textureView)
    override fun getVideoSize(): VideoSize = activePlayer.videoSize
    override fun getSurfaceSize(): Media3Size = activePlayer.surfaceSize
    override fun getCurrentCues(): CueGroup = activePlayer.currentCues
    override fun getDeviceInfo(): DeviceInfo = activePlayer.deviceInfo
    override fun getDeviceVolume(): Int = activePlayer.deviceVolume
    override fun isDeviceMuted(): Boolean = activePlayer.isDeviceMuted
    override fun setDeviceVolume(volume: Int) = activePlayer.setDeviceVolume(volume)
    override fun setDeviceVolume(volume: Int, flags: Int) = activePlayer.setDeviceVolume(volume, flags)
    override fun increaseDeviceVolume() = activePlayer.increaseDeviceVolume()
    override fun increaseDeviceVolume(flags: Int) = activePlayer.increaseDeviceVolume(flags)
    override fun decreaseDeviceVolume() = activePlayer.decreaseDeviceVolume()
    override fun decreaseDeviceVolume(flags: Int) = activePlayer.decreaseDeviceVolume(flags)
    override fun setDeviceMuted(muted: Boolean) = activePlayer.setDeviceMuted(muted)
    override fun setDeviceMuted(muted: Boolean, flags: Int) = activePlayer.setDeviceMuted(muted, flags)
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = activePlayer.setAudioAttributes(audioAttributes, handleAudioFocus)

    /**
     * Swaps [activePlayer] for [newPlayer], re-registers all tracked listeners on the new
     * player, removes them from the old one, and fires [onActivePlayerChanged] so the
     * service can migrate [AnalyticsListener]s and update its own player reference.
     */
    private fun installActivePlayer(newPlayer: ExoPlayer) {
        val old = activePlayer
        trackedListeners.forEach { old.removeListener(it) }
        activePlayer = newPlayer
        trackedListeners.forEach { newPlayer.addListener(it) }
        onActivePlayerChanged?.invoke(newPlayer, old)
    }

    /**
     * Starts the background loop that polls [activePlayer]'s position and triggers a
     * crossfade when remaining time falls inside the crossfade window. Safe to call
     * multiple times — duplicate calls are ignored.
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

    /** Stops the background position-monitor without releasing any players. */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Runs on every monitor tick. All conditions must pass or the check is silently skipped.
     */
    private fun checkAndTriggerCrossfade() {
        if (isCrossfading) return
        if (!AudioPreferences.isCrossfadeEnabled()) return

        // Gapless and crossfade both touch the track boundary — let gapless win when it is on.
        if (AudioPreferences.isGaplessPlaybackEnabled()) return

        // Repeat-one just loops the same song — there is nothing new to pre-load.
        if (PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_ONE) return

        val player = activePlayer
        if (!player.isPlaying) return

        val duration = player.duration
        if (duration <= 0) return

        val hasNext = player.hasNextMediaItem()
                || PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_QUEUE
        if (!hasNext) return

        val remaining = duration - player.contentPosition
        val window = AudioPreferences.getCrossfadeDurationMs().toLong()

        if (remaining in 1..window) {
            startCrossfade()
        }
    }

    /**
     * Loads the next song onto [standbyPlayer] and starts the paired fade-out/fade-in
     * animations. Both animations run simultaneously for the full crossfade duration.
     */
    private fun startCrossfade() {
        if (isCrossfading) return
        isCrossfading = true

        val active = activePlayer
        val standby = standbyPlayer

        val nextIndex = active.currentMediaItemIndex + 1
        val nextItems: List<MediaItem> = when {
            nextIndex < active.mediaItemCount -> {
                (nextIndex until active.mediaItemCount).map { active.getMediaItemAt(it) }
            }
            PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_QUEUE
                    && active.mediaItemCount > 0 -> {
                (0 until active.mediaItemCount).map { active.getMediaItemAt(it) }
            }
            else -> emptyList()
        }

        if (nextItems.isEmpty()) {
            isCrossfading = false
            return
        }

        // Mirror speed, pitch, and silence settings so the incoming song sounds identical.
        standby.playbackParameters = active.playbackParameters
        standby.repeatMode = active.repeatMode
        standby.skipSilenceEnabled = active.skipSilenceEnabled
        standby.setMediaItems(nextItems.toMutableList(), 0, 0L)
        standby.volume = 0f
        standby.playWhenReady = true
        standby.prepare()

        val fadeDuration = AudioPreferences.getCrossfadeDurationMs().toLong()

        val fadeOut = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = fadeDuration
            interpolator = LinearInterpolator()
            addUpdateListener { active.volume = it.animatedValue as Float }
        }

        val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeDuration
            interpolator = LinearInterpolator()
            addUpdateListener { standby.volume = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    completeCrossfade(outgoing = active, incoming = standby)
                }
            })
        }

        onCrossfadeStarted?.invoke()
        fadeOut.start()
        fadeIn.start()
    }

    /**
     * Finishes the crossfade by making the incoming deck the new [activePlayer].
     * The outgoing deck is reset to an idle, silent state so it can serve as the
     * standby for the next crossfade.
     */
    private fun completeCrossfade(outgoing: ExoPlayer, incoming: ExoPlayer) {
        // Hand off tracked listeners from the outgoing deck to the incoming one.
        installActivePlayer(incoming)
        standbyPlayer = outgoing

        // Wipe the outgoing deck so it holds no resources from the previous song.
        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.volume = 1f

        isCrossfading = false
    }

    /**
     * Tears down and rebuilds both player instances using [playerFactory]. Call this when
     * a setting that requires a full engine restart changes — for example the audio decoder
     * or hi-res output mode.
     *
     * Playback state is restored on the new active player so the user does not notice
     * the rebuild. All tracked [Listener]s are automatically moved to the new player.
     *
     * @param mediaItems    Full queue to restore.
     * @param currentIndex  Song index that was playing before the rebuild.
     * @param currentPos    Playback position in milliseconds.
     * @param playWhenReady Whether the player should resume immediately after restore.
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

        // Release old players AFTER building new ones to avoid any brief resource gap.
        val oldActive = activePlayer
        val oldStandby = standbyPlayer

        installActivePlayer(newActive)
        standbyPlayer = newStandby

        oldActive.release()
        oldStandby.release()

        if (mediaItems.isNotEmpty()) {
            newActive.setMediaItems(mediaItems.toMutableList(), currentIndex, currentPos)
            newActive.playWhenReady = playWhenReady
            newActive.prepare()
        }

        startMonitoring()
    }

    /**
     * Releases both player instances and stops the position monitor. Call this when
     * the service is destroying and playback will not resume.
     */
    fun releaseAll() {
        stopMonitoring()
        activePlayer.release()
        standbyPlayer.release()
    }
}