package app.simple.felicity.repository.managers

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import app.simple.felicity.repository.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaManager {

    private const val TAG = "MediaManager"
    private var mediaController: MediaController? = null
    private var songs: List<Song> = emptyList()
    private var currentSongPosition: Int = 0
        set(value) {
            if (value in songs.indices) {
                field = value
                CoroutineScope(Dispatchers.Main).launch {
                    _songPositionFlow.emit(value)
                }
            } else {
                Log.i(TAG, "Invalid song position: $value. Must be between 0 and ${songs.size - 1}.")
            }
        }

    private var seekJob: Job? = null

    private val _songListFlow = MutableSharedFlow<List<Song>>(replay = 1)
    private val _songPositionFlow = MutableSharedFlow<Int>(replay = 1)
    private val _songSeekPositionFlow = MutableSharedFlow<Long>(replay = 1)
    private val _playbackStateFlow = MutableSharedFlow<Int>(replay = 1)

    val songListFlow = _songListFlow
    val songPositionFlow = _songPositionFlow
    val songSeekPositionFlow = _songSeekPositionFlow
    val playbackStateFlow = _playbackStateFlow

    fun setMediaController(controller: MediaController) {
        mediaController = controller
    }

    fun clearMediaController() {
        mediaController = null
    }

    fun setSongs(songs: List<Song>, position: Int = 0) {
        this.songs = songs
        currentSongPosition = position
        playCurrent()
    }

    fun getSongs(): List<Song> = songs

    fun getCurrentSong(): Song? = songs.getOrNull(currentSongPosition)

    private fun playCurrent() {
        mediaController?.let { controller ->
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
            controller.setMediaItems(mediaItems, currentSongPosition, 0L)
            controller.prepare()
            // controller.play()

            startSeekPositionUpdates()
        }
    }

    fun playSong(song: Song) {
        val index = songs.indexOf(song)
        if (index != -1) {
            currentSongPosition = index
            playCurrent()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun play() {
        mediaController?.play()
    }

    fun stop() {
        mediaController?.stop()
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
        return mediaController?.currentPosition ?: 0L
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
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

    fun getDuration(): Long {
        return getCurrentSong()?.duration ?: 0L
    }

    fun getSongAt(position: Int): Song? {
        return if (position in songs.indices) {
            songs[position]
        } else {
            Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            null
        }
    }

    fun startSeekPositionUpdates(intervalMs: Long = 1000L) {
        seekJob?.cancel()
        seekJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val position = withContext(Dispatchers.Main) { getSeekPosition() }
                _songSeekPositionFlow.emit(position)
                delay(intervalMs)
            }
        }
    }

    fun stopSeekPositionUpdates() {
        seekJob?.cancel()
        seekJob = null
    }

    fun notifyPlaybackState(state: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            _playbackStateFlow.emit(state)
        }
    }
}