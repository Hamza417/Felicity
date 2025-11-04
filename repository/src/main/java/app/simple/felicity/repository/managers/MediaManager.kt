package app.simple.felicity.repository.managers

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.models.Song
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
import kotlin.math.max

object MediaManager {

    private const val TAG = "MediaManager"

    // Single app-scoped Main dispatcher scope to avoid leaking ad-hoc scopes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaController: MediaController? = null

    // Backing store for the queue provided by UI/db. Treat as read-only outside.
    private var songs: List<Song> = emptyList()

    // Current queue index. Setter also emits to observers when valid.
    private var currentSongPosition: Int = 0
        set(value) {
            if (value in songs.indices) {
                field = value
                // Emit position change to observers on the manager scope
                scope.launch {
                    _songPositionFlow.emit(value)
                }
            } else {
                Log.i(TAG, "Invalid song position: $value. Must be between 0 and ${songs.size - 1}.")
            }
        }

    private var seekJob: Job? = null

    // Flows are mutable internally but exposed as read-only to callers.
    private val _songListFlow = MutableSharedFlow<List<Song>>(replay = 1)
    private val _songPositionFlow = MutableSharedFlow<Int>(replay = 1)
    private val _songSeekPositionFlow = MutableSharedFlow<Long>(replay = 1)
    private val _playbackStateFlow = MutableSharedFlow<Int>(replay = 1)

    val songListFlow: SharedFlow<List<Song>> = _songListFlow.asSharedFlow()
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
    fun setSongs(songs: List<Song>, position: Int = 0, startPositionMs: Long = 0L) {
        this.songs = songs
        val clampedPosition = if (songs.isEmpty()) 0 else position.coerceIn(0, songs.size - 1)
        currentSongPosition = clampedPosition

        if (songs.isNotEmpty()) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.uri)
                    .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setArtist(song.artist)
                                .setTitle(song.title)
                                .build()
                    )
                    .build()
            }

            mediaController?.setMediaItems(mediaItems, currentSongPosition, startPositionMs)
            mediaController?.prepare()
            startSeekPositionUpdates()
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

    fun getSongs(): List<Song> = songs

    fun getCurrentSong(): Song? = songs.getOrNull(currentSongPosition)

    fun playCurrent() {
        mediaController?.seekToDefaultPosition(currentSongPosition)
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun playSong(song: Song) {
        // Prefer matching by stable id to avoid issues with data class equality or different instances
        val index = songs.indexOfFirst { it.id == song.id }
        if (index != -1) {
            currentSongPosition = index
            playCurrent()
        } else {
            Log.w(TAG, "playSong: Song not found in current list: ${song.id}")
        }
    }

    fun pause() {
        mediaController?.pause()
        // Do not stop seek updates; keep them running so UI stays in sync while paused
        // stopSeekPositionUpdates()
    }

    fun play() {
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun stop() {
        mediaController?.stop()
        stopSeekPositionUpdates()
    }

    fun flipState() {
        if (mediaController?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (songs.isNotEmpty()) {
            currentSongPosition = (currentSongPosition + 1) % songs.size
            playCurrent()
        }
    }

    fun previous() {
        if (songs.isNotEmpty()) {
            currentSongPosition = if (currentSongPosition == 0) songs.size - 1 else currentSongPosition - 1
            playCurrent()
        }
    }

    fun getSeekPosition(): Long {
        val position = mediaController?.currentPosition ?: 0L
        val duration = getDuration()
        // Clamp to [0, duration] when duration is known
        return if (duration > 0L) position.coerceIn(0L, duration) else max(0L, position)
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
        if (position in songs.indices) {
            currentSongPosition = position
            playCurrent()
        } else {
            Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
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

    fun getSongAt(position: Int): Song? {
        return if (position in songs.indices) {
            songs[position]
        } else {
            Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            null
        }
    }

    /**
     * Emit seek position periodically while playing to keep UI in sync.
     */
    fun startSeekPositionUpdates(intervalMs: Long = 1000L) {
        seekJob?.cancel()
        seekJob = scope.launch {
            var lastEmittedPosition: Long? = null
            while (isActive) {
                val position = getSeekPosition()
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
        // Keep seek updates running for PLAYING, PAUSED, and BUFFERING
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> startSeekPositionUpdates()
            MediaConstants.PLAYBACK_PAUSED -> startSeekPositionUpdates()
            MediaConstants.PLAYBACK_BUFFERING -> {
                // Keep existing job running; position may still be valid
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
            currentSongPosition = position
        } else {
            Log.w(TAG, "notifyCurrentPosition: Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
        }
    }
}