package app.simple.felicity.engine.wrapper

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.simple.felicity.preferences.AudioPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * @author Hamza417
 *
 * A [ForwardingPlayer] that keeps two [ExoPlayer] instances alive
 * at the same time — think of them as deck A and deck B on a DJ mixer. While one deck is
 * playing a song, the other silently loads the next one so they can overlap smoothly when
 * the track is almost over or when the user skips forward or backward.
 *
 * Because [ForwardingPlayer] stores its wrapped player in a private
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
 */
@Suppress("DEPRECATION")
@OptIn(UnstableApi::class)
class FelicityPlayerWrapper private constructor(
        initialPlayer: ExoPlayer,
        private val playerFactory: () -> ExoPlayer,
        private val wrapperScope: CoroutineScope
) : ForwardingPlayer(initialPlayer) {

    constructor(playerFactory: () -> ExoPlayer, scope: CoroutineScope) : this(playerFactory(), playerFactory, scope)

    data class CrossfadeTransitionInfo(
            val fromMediaItem: MediaItem?,
            val toMediaItem: MediaItem?,
            val fromIndex: Int,
            val toIndex: Int,
            val fromPositionMs: Long,
            val fromDurationMs: Long,
            val wasPlaying: Boolean,
            val reason: Int
    )

    var onActivePlayerChanged: ((newActive: ExoPlayer, oldActive: ExoPlayer) -> Unit)? = null
    var onCrossfadeTransition: ((CrossfadeTransitionInfo) -> Unit)? = null

    val activePlayer: ExoPlayer
        get() = currentActivePlayer

    private var currentActivePlayer: ExoPlayer = initialPlayer
    private var currentInactivePlayer: ExoPlayer = playerFactory()
    private val trackedListeners = linkedSetOf<Player.Listener>()

    private var monitorJob: Job? = null
    private var crossfadeJob: Job? = null
    private var requestedVolume: Float = initialPlayer.volume

    fun startMonitoring() {
        if (monitorJob?.isActive == true) {
            return
        }

        monitorJob = wrapperScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                maybeCrossfadeIntoNextTrack()
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    fun rebuildPlayers(
            mediaItems: List<MediaItem>,
            currentIndex: Int,
            currentPositionMs: Long,
            playWhenReady: Boolean
    ) {
        cancelCrossfade()

        val oldActive = currentActivePlayer
        val oldInactive = currentInactivePlayer
        val newActive = playerFactory()
        val newInactive = playerFactory()

        copyRuntimeState(oldActive, newActive)
        copyRuntimeState(oldActive, newInactive)
        mirrorQueueState(newActive, mediaItems, currentIndex, currentPositionMs)
        mirrorQueueState(newInactive, mediaItems, currentIndex, currentPositionMs)

        if (mediaItems.isNotEmpty()) {
            newActive.prepare()
            if (playWhenReady) {
                newActive.playWhenReady = true
            }
        }

        trackedListeners.forEach { listener ->
            oldActive.removeListener(listener)
            oldInactive.removeListener(listener)
            newActive.addListener(listener)
        }

        currentActivePlayer = newActive
        currentInactivePlayer = newInactive
        requestedVolume = oldActive.volume
        currentActivePlayer.volume = requestedVolume
        currentInactivePlayer.volume = requestedVolume

        onActivePlayerChanged?.invoke(newActive, oldActive)

        oldActive.release()
        oldInactive.release()
    }

    fun releaseAll() {
        monitorJob?.cancel()
        crossfadeJob?.cancel()
        currentActivePlayer.release()
        currentInactivePlayer.release()
    }

    override fun release() {
        releaseAll()
    }

    override fun addListener(listener: Player.Listener) {
        if (trackedListeners.add(listener)) {
            currentActivePlayer.addListener(listener)
        }
    }

    override fun removeListener(listener: Player.Listener) {
        trackedListeners.remove(listener)
        currentActivePlayer.removeListener(listener)
        currentInactivePlayer.removeListener(listener)
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return currentActivePlayer.isCommandAvailable(command)
    }

    override fun canAdvertiseSession(): Boolean {
        return currentActivePlayer.canAdvertiseSession()
    }

    override fun getAvailableCommands(): Player.Commands {
        return currentActivePlayer.availableCommands
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        cancelCrossfade()
        currentActivePlayer.setMediaItem(mediaItem)
        currentInactivePlayer.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        cancelCrossfade()
        currentActivePlayer.setMediaItem(mediaItem, startPositionMs)
        currentInactivePlayer.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        cancelCrossfade()
        currentActivePlayer.setMediaItem(mediaItem, resetPosition)
        currentInactivePlayer.setMediaItem(mediaItem, resetPosition)
    }

    override fun prepare() {
        currentActivePlayer.prepare()
    }

    override fun play() {
        currentActivePlayer.play()
    }

    override fun pause() {
        currentActivePlayer.pause()
    }

    override fun stop() {
        cancelCrossfade()
        currentActivePlayer.stop()
        currentInactivePlayer.stop()
    }

    override fun seekTo(positionMs: Long) {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }
        currentActivePlayer.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }

        if (!shouldCrossfadeTo(mediaItemIndex)) {
            currentActivePlayer.seekTo(mediaItemIndex, positionMs)
            currentInactivePlayer.seekTo(mediaItemIndex, positionMs)
            return
        }

        startCrossfade(mediaItemIndex, positionMs, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    override fun seekToDefaultPosition() {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }
        currentActivePlayer.seekToDefaultPosition()
        currentInactivePlayer.seekToDefaultPosition(currentActivePlayer.currentMediaItemIndex)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }

        if (!shouldCrossfadeTo(mediaItemIndex)) {
            currentActivePlayer.seekToDefaultPosition(mediaItemIndex)
            currentInactivePlayer.seekToDefaultPosition(mediaItemIndex)
            return
        }

        startCrossfade(mediaItemIndex, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    override fun seekToNextMediaItem() {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }

        val targetIndex = resolveNextIndex() ?: run {
            currentActivePlayer.seekToNextMediaItem()
            return
        }

        if (!shouldCrossfadeTo(targetIndex)) {
            currentActivePlayer.seekToNextMediaItem()
            currentInactivePlayer.seekTo(targetIndex, 0L)
            return
        }

        startCrossfade(targetIndex, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    override fun seekToPreviousMediaItem() {
        if (crossfadeJob?.isActive == true) {
            cancelCrossfade()
        }

        val targetIndex = resolvePreviousIndex() ?: run {
            currentActivePlayer.seekToPreviousMediaItem()
            return
        }

        if (!shouldCrossfadeTo(targetIndex)) {
            currentActivePlayer.seekToPreviousMediaItem()
            currentInactivePlayer.seekTo(targetIndex, 0L)
            return
        }

        startCrossfade(targetIndex, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    override fun clearMediaItems() {
        cancelCrossfade()
        currentActivePlayer.clearMediaItems()
        currentInactivePlayer.clearMediaItems()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        cancelCrossfade()
        currentActivePlayer.setMediaItems(mediaItems, resetPosition)
        currentInactivePlayer.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        cancelCrossfade()
        currentActivePlayer.setMediaItems(mediaItems)
        currentInactivePlayer.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        cancelCrossfade()
        currentActivePlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        currentInactivePlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        currentActivePlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
        currentInactivePlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        currentActivePlayer.addMediaItem(mediaItem)
        currentInactivePlayer.addMediaItem(mediaItem)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        currentActivePlayer.addMediaItem(index, mediaItem)
        currentInactivePlayer.addMediaItem(index, mediaItem)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        currentActivePlayer.addMediaItems(mediaItems)
        currentInactivePlayer.addMediaItems(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        currentActivePlayer.addMediaItems(index, mediaItems)
        currentInactivePlayer.addMediaItems(index, mediaItems)
    }

    override fun removeMediaItem(index: Int) {
        currentActivePlayer.removeMediaItem(index)
        currentInactivePlayer.removeMediaItem(index)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        currentActivePlayer.removeMediaItems(fromIndex, toIndex)
        currentInactivePlayer.removeMediaItems(fromIndex, toIndex)
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        currentActivePlayer.moveMediaItem(currentIndex, newIndex)
        currentInactivePlayer.moveMediaItem(currentIndex, newIndex)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        currentActivePlayer.moveMediaItems(fromIndex, toIndex, newIndex)
        currentInactivePlayer.moveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        currentActivePlayer.replaceMediaItem(index, mediaItem)
        currentInactivePlayer.replaceMediaItem(index, mediaItem)
    }

    override fun getPlaybackState(): Int {
        return currentActivePlayer.playbackState
    }

    override fun getPlaybackSuppressionReason(): Int {
        return currentActivePlayer.playbackSuppressionReason
    }

    override fun getPlayerError(): PlaybackException? {
        return currentActivePlayer.playerError
    }

    override fun getPlayWhenReady(): Boolean {
        return currentActivePlayer.playWhenReady
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        currentActivePlayer.playWhenReady = playWhenReady
        if (!playWhenReady) {
            currentInactivePlayer.playWhenReady = false
        }
    }

    override fun isPlaying(): Boolean {
        return currentActivePlayer.isPlaying
    }

    override fun isLoading(): Boolean {
        return currentActivePlayer.isLoading
    }

    override fun getCurrentPosition(): Long {
        return currentActivePlayer.currentPosition
    }

    override fun getBufferedPosition(): Long {
        return currentActivePlayer.bufferedPosition
    }

    override fun getBufferedPercentage(): Int {
        return currentActivePlayer.bufferedPercentage
    }

    override fun getTotalBufferedDuration(): Long {
        return currentActivePlayer.totalBufferedDuration
    }

    override fun getDuration(): Long {
        return currentActivePlayer.duration
    }

    override fun getContentDuration(): Long {
        return currentActivePlayer.contentDuration
    }

    override fun getContentPosition(): Long {
        return currentActivePlayer.contentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return currentActivePlayer.contentBufferedPosition
    }

    override fun getCurrentMediaItemIndex(): Int {
        return currentActivePlayer.currentMediaItemIndex
    }

    override fun getCurrentPeriodIndex(): Int {
        return currentActivePlayer.currentPeriodIndex
    }

    @Deprecated("Use currentMediaItemIndex instead")
    override fun getCurrentWindowIndex(): Int {
        return currentActivePlayer.currentWindowIndex
    }

    @Deprecated("Use nextMediaItemIndex instead")
    override fun getNextWindowIndex(): Int {
        return currentActivePlayer.nextWindowIndex
    }

    override fun getNextMediaItemIndex(): Int {
        return currentActivePlayer.nextMediaItemIndex
    }

    @Deprecated("Use previousMediaItemIndex instead")
    override fun getPreviousWindowIndex(): Int {
        return currentActivePlayer.previousWindowIndex
    }

    override fun getPreviousMediaItemIndex(): Int {
        return currentActivePlayer.previousMediaItemIndex
    }

    override fun getMediaItemCount(): Int {
        return currentActivePlayer.mediaItemCount
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return currentActivePlayer.getMediaItemAt(index)
    }

    override fun hasNextMediaItem(): Boolean {
        return resolveNextIndex() != null
    }

    override fun hasPreviousMediaItem(): Boolean {
        return resolvePreviousIndex() != null
    }

    override fun getRepeatMode(): Int {
        return currentActivePlayer.repeatMode
    }

    override fun setRepeatMode(repeatMode: Int) {
        currentActivePlayer.repeatMode = repeatMode
        currentInactivePlayer.repeatMode = repeatMode
    }

    override fun getShuffleModeEnabled(): Boolean {
        return currentActivePlayer.shuffleModeEnabled
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        currentActivePlayer.shuffleModeEnabled = shuffleModeEnabled
        currentInactivePlayer.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return currentActivePlayer.playbackParameters
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        currentActivePlayer.playbackParameters = playbackParameters
        currentInactivePlayer.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentActivePlayer.setPlaybackSpeed(speed)
        currentInactivePlayer.setPlaybackSpeed(speed)
    }

    override fun getMediaMetadata(): MediaMetadata {
        return currentActivePlayer.mediaMetadata
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return currentActivePlayer.playlistMetadata
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        currentActivePlayer.playlistMetadata = mediaMetadata
        currentInactivePlayer.playlistMetadata = mediaMetadata
    }

    override fun getCurrentManifest(): Any? {
        return currentActivePlayer.currentManifest
    }

    override fun isCurrentMediaItemDynamic(): Boolean {
        return currentActivePlayer.isCurrentMediaItemDynamic
    }

    @Deprecated("Use isCurrentMediaItemDynamic instead")
    override fun isCurrentWindowDynamic(): Boolean {
        return currentActivePlayer.isCurrentWindowDynamic
    }

    override fun isCurrentMediaItemLive(): Boolean {
        return currentActivePlayer.isCurrentMediaItemLive
    }

    @Deprecated("Use isCurrentMediaItemLive instead")
    override fun isCurrentWindowLive(): Boolean {
        return currentActivePlayer.isCurrentWindowLive
    }

    override fun getCurrentLiveOffset(): Long {
        return currentActivePlayer.currentLiveOffset
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        return currentActivePlayer.isCurrentMediaItemSeekable
    }

    @Deprecated("Use isCurrentMediaItemSeekable instead")
    override fun isCurrentWindowSeekable(): Boolean {
        return currentActivePlayer.isCurrentWindowSeekable
    }

    override fun isPlayingAd(): Boolean {
        return currentActivePlayer.isPlayingAd
    }

    override fun getCurrentAdGroupIndex(): Int {
        return currentActivePlayer.currentAdGroupIndex
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return currentActivePlayer.currentAdIndexInAdGroup
    }

    override fun getAudioAttributes(): AudioAttributes {
        return currentActivePlayer.audioAttributes
    }

    override fun getAudioSessionId(): Int {
        return currentActivePlayer.audioSessionId
    }

    override fun getVolume(): Float {
        return requestedVolume
    }

    override fun setVolume(volume: Float) {
        requestedVolume = volume
        if (crossfadeJob?.isActive == true) {
            currentActivePlayer.volume = min(volume, currentActivePlayer.volume)
            currentInactivePlayer.volume = min(volume, currentInactivePlayer.volume)
        } else {
            currentActivePlayer.volume = volume
            currentInactivePlayer.volume = volume
        }
    }

    override fun mute() {
        currentActivePlayer.mute()
        currentInactivePlayer.mute()
    }

    override fun unmute() {
        currentActivePlayer.unmute()
        currentInactivePlayer.unmute()
    }

    override fun getVideoSize(): VideoSize {
        return currentActivePlayer.videoSize
    }

    override fun getSurfaceSize(): Size {
        return currentActivePlayer.surfaceSize
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return currentActivePlayer.trackSelectionParameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        currentActivePlayer.trackSelectionParameters = parameters
        currentInactivePlayer.trackSelectionParameters = parameters
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return currentActivePlayer.currentMediaItem
    }

    override fun getCurrentTimeline(): Timeline {
        return currentActivePlayer.currentTimeline
    }

    override fun getCurrentTracks(): Tracks {
        return currentActivePlayer.currentTracks
    }

    override fun getCurrentCues() = currentActivePlayer.currentCues

    override fun getDeviceInfo(): DeviceInfo {
        return currentActivePlayer.deviceInfo
    }

    override fun getDeviceVolume(): Int {
        return currentActivePlayer.deviceVolume
    }

    override fun isDeviceMuted(): Boolean {
        return currentActivePlayer.isDeviceMuted
    }

    private fun maybeCrossfadeIntoNextTrack() {
        if (!shouldAutoCrossfade()) {
            return
        }

        val duration = currentActivePlayer.duration
        if (duration <= 0L) {
            return
        }

        val remaining = duration - currentActivePlayer.currentPosition
        if (remaining <= AudioPreferences.getCrossfadeDurationMs()) {
            resolveNextIndex()?.let {
                startCrossfade(it, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
        }
    }

    private fun shouldAutoCrossfade(): Boolean {
        return crossfadeJob?.isActive != true
                && currentActivePlayer.isPlaying
                && shouldUseCrossfade()
                && resolveNextIndex() != null
    }

    private fun shouldCrossfadeTo(targetIndex: Int): Boolean {
        return crossfadeJob?.isActive != true
                && shouldUseCrossfade()
                && currentActivePlayer.isPlaying
                && targetIndex in 0 until currentActivePlayer.mediaItemCount
                && targetIndex != currentActivePlayer.currentMediaItemIndex
    }

    private fun shouldUseCrossfade(): Boolean {
        return AudioPreferences.isCrossfadeEnabled()
                && !AudioPreferences.isGaplessPlaybackEnabled()
                && currentActivePlayer.repeatMode != Player.REPEAT_MODE_ONE
                && currentActivePlayer.mediaItemCount > 1
    }

    private fun resolveNextIndex(): Int? {
        val count = currentActivePlayer.mediaItemCount
        if (count <= 1) {
            return null
        }

        val nextIndex = currentActivePlayer.currentMediaItemIndex + 1
        return when {
            nextIndex < count -> nextIndex
            currentActivePlayer.repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> null
        }
    }

    private fun resolvePreviousIndex(): Int? {
        val count = currentActivePlayer.mediaItemCount
        if (count <= 1) {
            return null
        }

        val previousIndex = currentActivePlayer.currentMediaItemIndex - 1
        return when {
            previousIndex >= 0 -> previousIndex
            currentActivePlayer.repeatMode == Player.REPEAT_MODE_ALL -> count - 1
            else -> null
        }
    }

    private fun startCrossfade(targetIndex: Int, targetPositionMs: Long, reason: Int) {
        cancelCrossfade()

        val outgoingPlayer = currentActivePlayer
        val incomingPlayer = currentInactivePlayer
        val durationMs = AudioPreferences.getCrossfadeDurationMs().toLong().coerceAtLeast(MIN_CROSSFADE_DURATION_MS)
        val transitionInfo = CrossfadeTransitionInfo(
                fromMediaItem = outgoingPlayer.currentMediaItem,
                toMediaItem = outgoingPlayer.getMediaItemAt(targetIndex),
                fromIndex = outgoingPlayer.currentMediaItemIndex,
                toIndex = targetIndex,
                fromPositionMs = outgoingPlayer.currentPosition,
                fromDurationMs = max(0L, outgoingPlayer.duration),
                wasPlaying = outgoingPlayer.playWhenReady,
                reason = reason
        )

        copyRuntimeState(outgoingPlayer, incomingPlayer)
        incomingPlayer.volume = 0f
        incomingPlayer.seekTo(targetIndex, targetPositionMs)
        incomingPlayer.prepare()
        incomingPlayer.playWhenReady = transitionInfo.wasPlaying

        moveTrackedListeners(outgoingPlayer, incomingPlayer)
        currentActivePlayer = incomingPlayer
        currentInactivePlayer = outgoingPlayer
        onActivePlayerChanged?.invoke(incomingPlayer, outgoingPlayer)
        onCrossfadeTransition?.invoke(transitionInfo)

        crossfadeJob = wrapperScope.launch(Dispatchers.Main.immediate) {
            if (transitionInfo.wasPlaying) {
                incomingPlayer.play()
            }

            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                incomingPlayer.volume = requestedVolume * progress
                outgoingPlayer.volume = requestedVolume * (1f - progress)

                if (progress >= 1f) {
                    break
                }

                delay(FADE_STEP_MS)
            }

            incomingPlayer.volume = requestedVolume
            outgoingPlayer.pause()
            outgoingPlayer.volume = requestedVolume
            outgoingPlayer.seekTo(incomingPlayer.currentMediaItemIndex, incomingPlayer.currentPosition)
        }
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        currentActivePlayer.volume = requestedVolume
        currentInactivePlayer.volume = requestedVolume
        currentInactivePlayer.pause()
    }

    private fun copyRuntimeState(from: ExoPlayer, to: ExoPlayer) {
        to.repeatMode = from.repeatMode
        to.shuffleModeEnabled = from.shuffleModeEnabled
        to.playbackParameters = from.playbackParameters
        to.trackSelectionParameters = from.trackSelectionParameters
        to.pauseAtEndOfMediaItems = from.pauseAtEndOfMediaItems
        to.skipSilenceEnabled = from.skipSilenceEnabled
        to.volume = requestedVolume
    }

    private fun mirrorQueueState(
            player: ExoPlayer,
            mediaItems: List<MediaItem>,
            currentIndex: Int,
            currentPositionMs: Long
    ) {
        if (mediaItems.isEmpty()) {
            player.clearMediaItems()
            return
        }

        player.setMediaItems(mediaItems, currentIndex.coerceAtLeast(0), currentPositionMs)
        player.seekTo(currentIndex.coerceAtLeast(0), currentPositionMs)
    }

    private fun moveTrackedListeners(from: ExoPlayer, to: ExoPlayer) {
        trackedListeners.forEach { listener ->
            from.removeListener(listener)
            to.addListener(listener)
        }
    }

    companion object {
        private const val MONITOR_INTERVAL_MS = 200L
        private const val FADE_STEP_MS = 16L
        private const val MIN_CROSSFADE_DURATION_MS = 500L
    }
}