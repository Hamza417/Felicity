package app.simple.felicity.repository.managers

import android.util.Log
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ProcessUtils.ensureOnMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

object MediaManager {

    private const val TAG = "MediaManager"

    // Single app-scoped Main dispatcher scope to avoid leaking ad-hoc scopes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaController: MediaController? = null

    // Backing store for the queue provided by UI/db. Treat as read-only outside.
    private var songs: List<Audio> = emptyList()

    // Current queue index. Setter also emits to observers when valid and changed.
    private var currentSongPosition: Int = 0
        set(value) {
            if (value in songs.indices) {
                if (field != value) {
                    field = value
                    // Emit position change to observers on the manager scope
                    scope.launch {
                        _songPositionFlow.emit(value)
                    }
                }
            } else {
                Log.i(TAG, "Invalid song position: $value. Must be between 0 and ${songs.size - 1}.")
            }
        }

    private var seekJob: Job? = null

    // Flows are mutable internally but exposed as read-only to callers.
    private val _songListFlow = MutableSharedFlow<List<Audio>>(replay = 1)
    private val _songPositionFlow = MutableSharedFlow<Int>(replay = 1)
    private val _songSeekPositionFlow = MutableSharedFlow<Long>(replay = 1)
    private val _playbackStateFlow = MutableSharedFlow<Int>(replay = 1)

    val songListFlow: SharedFlow<List<Audio>> = _songListFlow.asSharedFlow()
    val songPositionFlow: SharedFlow<Int> = _songPositionFlow.asSharedFlow()
    val songSeekPositionFlow: SharedFlow<Long> = _songSeekPositionFlow.asSharedFlow()
    val playbackStateFlow: SharedFlow<Int> = _playbackStateFlow.asSharedFlow()

    fun setMediaController(controller: MediaController) {
        mediaController = controller
        // If controller is already playing when set, ensure seek updates are running
        if (controller.isPlaying) startSeekPositionUpdates() else stopSeekPositionUpdates()
    }

    fun clearMediaController() {
        stopSeekPositionUpdates()
        mediaController = null
    }

    /**
     * Provide a new queue to the controller and notify UI. Position is clamped to valid range.
     * Note: Emission order between list and position is not strictly guaranteed due to coroutines,
     * but replay=1 on both flows ensures UI will observe the latest of each.
     */
    fun setSongs(audios: List<Audio>, position: Int = 0, startPositionMs: Long = 0L) {
        Log.d(TAG, "setSongs called: count=${audios.size}, position=$position, startPositionMs=$startPositionMs")

        this.songs = audios
        val clampedPosition = if (audios.isEmpty()) 0 else position.coerceIn(0, audios.size - 1)
        currentSongPosition = clampedPosition

        // Line 83 inside setSongs
        if (audios.isNotEmpty()) {
            // Move heavy mapping to background thread
            scope.launch {
                val mediaItems = withContext(Dispatchers.Default) {
                    audios.map { audio ->
                        val uri = File(audio.path).toUri()
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(uri)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setArtist(audio.artist)
                                        .setTitle(audio.title)
                                        .build()
                            )
                            .build()
                    }
                }

                // Back on Main Thread to set items
                if (mediaController != null) {
                    mediaController?.setMediaItems(mediaItems, currentSongPosition, startPositionMs)
                    mediaController?.prepare()
                    // mediaController?.play() // Auto-play when setting new list?
                }
                startSeekPositionUpdates()
            }
        } else {
            // Clear controller playlist if applicable and keep UI state consistent
            mediaController?.clearMediaItems()
            mediaController?.stop()
            stopSeekPositionUpdates()
            scope.launch { _songPositionFlow.emit(0) }
        }

        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    fun getSongs(): List<Audio> = songs

    fun getCurrentSong(): Audio? = songs.getOrNull(currentSongPosition)

    /**
     * Returns true if the given list has the exact same song IDs in the same order as the current queue.
     */
    fun isSameQueue(audios: List<Audio>): Boolean {
        if (audios.size != songs.size) return false
        return audios.indices.all { audios[it].id == songs[it].id }
    }

    /**
     * Updates the internal song list and emits the new list to observers WITHOUT resetting
     * the media controller or interrupting playback. Used when the queue composition changes
     * but the currently-playing song should continue uninterrupted.
     */
    fun updateQueueSilently(audios: List<Audio>, newPosition: Int) {
        this.songs = audios
        val clampedPosition = if (audios.isEmpty()) 0 else newPosition.coerceIn(0, audios.size - 1)
        currentSongPosition = clampedPosition

        // Rebuild media items on the controller to reflect the new queue, but seek to the
        // existing track so playback is not interrupted.
        if (audios.isNotEmpty()) {
            scope.launch {
                val mediaItems = withContext(Dispatchers.Default) {
                    audios.map { audio ->
                        val uri = File(audio.path).toUri()
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(uri)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setArtist(audio.artist)
                                        .setTitle(audio.title)
                                        .build()
                            )
                            .build()
                    }
                }

                val currentPositionMs = mediaController?.currentPosition ?: 0L
                mediaController?.setMediaItems(mediaItems, clampedPosition, currentPositionMs)
                mediaController?.prepare()
                mediaController?.play()
                startSeekPositionUpdates()
            }
        }

        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    // Line 133
    fun playCurrent() {
        // Only seek if we are NOT at the correct index already
        if (mediaController?.currentMediaItemIndex != currentSongPosition) {
            mediaController?.seekTo(currentSongPosition, 0L)
        }
        // Just play. If we are already there, this resumes perfectly.
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun playSong(audio: Audio) {
        // Prefer matching by stable id to avoid issues with data class equality or different instances
        val index = songs.indexOfFirst { it.id == audio.id }
        if (index != -1) {
            currentSongPosition = index
            playCurrent()
        } else {
            Log.w(TAG, "playSong: Audio not found in current list: ${audio.id}")
        }
    }

    fun pause() {
        mediaController?.pause()
        // Stop seek updates when paused to reduce unnecessary processing
        // UI will get the final position from the playback state change
        stopSeekPositionUpdates()
    }

    fun play() {
        if (mediaController == null) {
            Log.w(TAG, "play() called but mediaController is null")
            return
        }
        if (songs.isEmpty()) {
            Log.w(TAG, "play() called but songs list is empty")
            return
        }
        Log.d(TAG, "play() called: currentPosition=$currentSongPosition, mediaItemCount=${mediaController?.mediaItemCount}")
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun stop() {
        mediaController?.stop()
        stopSeekPositionUpdates()
    }

    fun isPlaying(): Boolean {
        return mediaController?.isPlaying == true
    }

    fun flipState() {
        if (mediaController == null) {
            Log.w(TAG, "flipState() called but mediaController is null")
            return
        }
        if (songs.isEmpty()) {
            Log.w(TAG, "flipState() called but songs list is empty")
            return
        }
        Log.d(TAG, "flipState() called: isPlaying=${mediaController?.isPlaying}, mediaItemCount=${mediaController?.mediaItemCount}")
        if (mediaController?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (mediaController?.hasNextMediaItem() == true) {
            mediaController?.seekToNextMediaItem()
            // Let ExoPlayer handle the transition naturally for gapless playback
            // Don't force position updates here
        }
    }

    fun previous() {
        if (mediaController?.hasPreviousMediaItem() == true) {
            mediaController?.seekToPreviousMediaItem()
            // Let ExoPlayer handle the transition naturally for gapless playback
            // Don't force position updates here
        }
    }

    @MainThread
    fun getSeekPosition(): Long {
        ensureOnMainThread {
            val position = mediaController?.currentPosition ?: 0L
            val duration = getDuration()
            // Clamp to [0, duration] when duration is known
            return if (duration > 0L) position.coerceIn(0L, duration) else max(0L, position)
        }
    }

    fun seekTo(position: Long) {
        val duration = getDuration()
        val clamped = if (duration > 0L) position.coerceIn(0L, duration) else max(0L, position)
        mediaController?.seekTo(clamped)
        // Optimistically emit the new seek position for responsive UI
        scope.launch { _songSeekPositionFlow.emit(clamped) }
    }

    fun getCurrentPosition(): Int {
        return currentSongPosition
    }

    fun getCurrentSongId(): Long? {
        return getCurrentSong()?.id
    }

    fun updatePosition(position: Int) {
        if (position != currentSongPosition) {
            if (position in songs.indices) {
                currentSongPosition = position
                // Let ExoPlayer handle the transition naturally for gapless playback
                // Only seek if the controller is not already on this track
                if (mediaController?.currentMediaItemIndex != position) {
                    mediaController?.seekTo(position, 0L)
                    // Only start playing if we were already playing, otherwise just prepare
                    if (mediaController?.isPlaying == true) {
                        startSeekPositionUpdates()
                    }
                }
            } else {
                Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            }
        } else {
            Log.i(TAG, "updatePosition called with current position: $position. No action taken.")
        }
    }

    /**
     * Prefer controller duration when available; fallback to model.
     */
    fun getDuration(): Long {
        val controllerDuration = mediaController?.duration ?: C.TIME_UNSET
        return when {
            controllerDuration != C.TIME_UNSET && controllerDuration >= 0L -> controllerDuration
            else -> getCurrentSong()?.duration ?: 0L
        }
    }

    fun getSongAt(position: Int): Audio? {
        return if (position in songs.indices) {
            songs[position]
        } else {
            Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            null
        }
    }

    /**
     * Emit seek position periodically while playing to keep UI in sync.
     * Only runs when actually needed to avoid overhead.
     */
    fun startSeekPositionUpdates(intervalMs: Long = 200L) {
        // Don't start multiple jobs - check if already running
        if (seekJob?.isActive == true) {
            return
        }

        seekJob?.cancel()
        seekJob = scope.launch {
            var lastEmittedPosition: Long? = null
            while (isActive) {
                val position = getSeekPosition()
                // Only emit if position actually changed to reduce overhead
                if (position != lastEmittedPosition) {
                    _songSeekPositionFlow.emit(position)
                    lastEmittedPosition = position
                }
                delay(intervalMs)
            }
        }
    }

    fun stopSeekPositionUpdates() {
        seekJob?.cancel()
        seekJob = null
    }

    /**
     * Service can push controller state here; we also drive seek updates based on it.
     */
    fun notifyPlaybackState(state: Int) {
        scope.launch {
            _playbackStateFlow.emit(state)
        }
        // Keep seek updates running ONLY for PLAYING state
        // For paused/buffering, stop updates to avoid interfering with ExoPlayer
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> startSeekPositionUpdates()
            MediaConstants.PLAYBACK_PAUSED -> {
                stopSeekPositionUpdates()
                // Emit final position when paused
                scope.launch {
                    _songSeekPositionFlow.emit(getSeekPosition())
                }
            }
            MediaConstants.PLAYBACK_BUFFERING -> {
                // Don't stop during buffering, but also don't restart if not running
                // ExoPlayer is handling buffer state, we shouldn't interfere
            }
            MediaConstants.PLAYBACK_STOPPED,
            MediaConstants.PLAYBACK_ENDED,
            MediaConstants.PLAYBACK_ERROR -> stopSeekPositionUpdates()
            else -> {
                // No-op
            }
        }
    }

    // Notify UI about current media item index changes originating from the player/service without reconfiguring playback
    fun notifyCurrentPosition(position: Int) {
        if (position in songs.indices) {
            // Check if position actually changed to avoid unnecessary emissions
            if (currentSongPosition != position) {
                // Directly update without using setter to avoid potential feedback loops
                // But we need to emit manually since we're not using the setter
                currentSongPosition = position
                scope.launch { _songPositionFlow.emit(position) }
            }
        } else {
            Log.w(TAG, "notifyCurrentPosition: Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
        }
    }
}