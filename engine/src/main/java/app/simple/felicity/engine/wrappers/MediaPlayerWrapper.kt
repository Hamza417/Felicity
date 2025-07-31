package app.simple.felicity.engine.wrappers

import android.content.Context
import android.media.MediaPlayer
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MediaPlayerWrapper(private val context: Context) : Player {
    private val mediaPlayer = MediaPlayer()
    private val listeners = mutableListOf<Player.Listener>()
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        mediaPlayer.reset()
        mediaPlayer.setDataSource(context, mediaItem.localConfiguration!!.uri)
        mediaPlayer.prepareAsync()
        playbackState = Player.STATE_BUFFERING
        // Notify listeners as needed
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun play() {
        mediaPlayer.start()
        playWhenReady = true
        playbackState = Player.STATE_READY
        // Notify listeners as needed
    }

    override fun pause() {
        mediaPlayer.pause()
        playWhenReady = false
        // Notify listeners as needed
    }

    override fun stop() {
        mediaPlayer.stop()
        playbackState = Player.STATE_IDLE
        // Notify listeners as needed
    }

    override fun release() {
        mediaPlayer.release()
        playbackState = Player.STATE_IDLE
        // Notify listeners as needed
    }

    override fun getPlaybackState(): Int = playbackState

    override fun isPlaying(): Boolean = mediaPlayer.isPlaying

    override fun getPlayWhenReady(): Boolean = playWhenReady

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        this.playWhenReady = playWhenReady
        if (playWhenReady) play() else pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer.seekTo(positionMs.toInt())
    }

    override fun getCurrentPosition(): Long = mediaPlayer.currentPosition.toLong()

    override fun getDuration(): Long = mediaPlayer.duration.toLong()

    override fun canAdvertiseSession(): Boolean = true
    override fun getAvailableCommands(): Commands {
        TODO("Not yet implemented")
    }

    override fun isCommandAvailable(command: Int): Boolean = true
    override fun prepare() {}
    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun isLoading(): Boolean = false
    override fun seekToDefaultPosition() {}
    override fun seekToDefaultPosition(mediaItemIndex: Int) {}
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
    override fun getSeekBackIncrement(): Long = 5000
    override fun seekBack() {}
    override fun getSeekForwardIncrement(): Long = 5000
    override fun seekForward() {}
    override fun hasPreviousMediaItem(): Boolean = false
    override fun seekToPreviousMediaItem() {}
    override fun getMaxSeekToPreviousPosition(): Long = 0
    override fun seekToPrevious() {}
    override fun hasNextMediaItem(): Boolean = false
    override fun seekToNextMediaItem() {}
    override fun seekToNext() {}
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
    override fun setPlaybackSpeed(speed: Float) {}
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentManifest(): Any? = null
    override fun getCurrentTimeline(): Timeline = Timeline.EMPTY
    override fun getCurrentPeriodIndex(): Int = 0
    override fun getCurrentWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getCurrentMediaItemIndex(): Int = 0
    override fun getNextWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getNextMediaItemIndex(): Int = 0
    override fun getPreviousWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getPreviousMediaItemIndex(): Int = 0
    override fun getCurrentMediaItem(): MediaItem? = null
    override fun getMediaItemCount(): Int = 0
    override fun getMediaItemAt(index: Int): MediaItem = MediaItem.EMPTY
    override fun getBufferedPosition(): Long = 0
    override fun getBufferedPercentage(): Int = 0
    override fun getTotalBufferedDuration(): Long = 0
    override fun isCurrentWindowDynamic(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentMediaItemDynamic(): Boolean = false
    override fun isCurrentWindowLive(): Boolean = false
    override fun isCurrentMediaItemLive(): Boolean = false
    override fun getCurrentLiveOffset(): Long = 0
    override fun isCurrentWindowSeekable(): Boolean = false
    override fun isCurrentMediaItemSeekable(): Boolean = false
    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = 0
    override fun getCurrentAdIndexInAdGroup(): Int = 0
    override fun getContentDuration(): Long = 0
    override fun getContentPosition(): Long = 0
    override fun getContentBufferedPosition(): Long = 0
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    override fun getVolume(): Float = 1.0f
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun setVideoTextureView(textureView: android.view.TextureView?) {}
    override fun clearVideoTextureView(textureView: android.view.TextureView?) {}
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun getSurfaceSize(): Size = Size.UNKNOWN
    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN
    override fun getDeviceVolume(): Int = 0
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int) {
        TODO("Not yet implemented")
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun increaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume(flags: Int) {}
    override fun decreaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun decreaseDeviceVolume(flags: Int) {}
    override fun setDeviceMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {}
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItem(index: Int) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    override fun clearMediaItems() {}
}