package app.simple.felicity.extensions.dialogs

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

abstract class MediaBottomDialogFragment : ScopedBottomSheetFragment() {

    private var lastSavedSeekPosition = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.songSeekPositionFlow.collect { position ->
                onSeekChanged(position)

                // Save to database every 5 seconds or 5% of duration, whichever is larger
                val song = MediaPlaybackManager.getCurrentSong()
                if (song != null) {
                    val threshold = maxOf(5000L, song.duration / 20) // 5 seconds or 5% of duration
                    if (abs(position - lastSavedSeekPosition) > threshold) {
                        lastSavedSeekPosition = position
                        saveCurrentPlaybackState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle(STARTED) ensures we re-subscribe and replay the last
            // emitted position every time the fragment comes back to the foreground.
            // This guarantees onAudio() fires on resume, clearing any stale highlights.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.songPositionFlow.collect { position ->
                    Log.d(TAG, "Song position: $position")
                    MediaPlaybackManager.getCurrentSong()?.let { song ->
                        onAudio(song)
                    }
                    onPositionChanged(position)
                    // Save state when song position changes
                    saveCurrentPlaybackState()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.songListFlow.collect { songs ->
                Log.d(TAG, "Song list updated: ${songs.size} songs")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.playbackStateFlow.collect { state ->
                onPlaybackStateChanged(state)
            }
        }
    }

    private fun saveCurrentPlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PlaybackStateManager.saveCurrentPlaybackState(requireContext(), TAG)
        }
    }

    protected fun setMediaItems(songs: List<Audio>, position: Int = 0) {
        // MediaPlaybackManager.setSongs(songs, position)
        MediaPlaybackManager.play()
        createSongHistoryDatabase(songs)
    }

    private fun createSongHistoryDatabase(songs: List<Audio>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val audioDatabase = AudioDatabase.getInstance(requireContext())
            PlaybackStateManager.savePlaybackState(
                    db = audioDatabase,
                    queueHash = songs.map { it.hash },
                    index = MediaPlaybackManager.getCurrentPosition(),
                    position = MediaPlaybackManager.getSeekPosition(),
                    shuffle = false,
                    repeat = 0
            )
        }
    }

    open fun onPlaybackStateChanged(state: Int) {
        Log.d(TAG, "Playback state changed: $state")
    }

    open fun onAudio(audio: Audio) {
        Log.d(TAG, "New song played: ${audio.title} by ${audio.artist}")
    }

    open fun onPositionChanged(position: Int) {
        Log.d(TAG, "Position changed: $position")
    }

    open fun onSeekChanged(seek: Long) {
        /* no-op */
    }

    companion object {
        private const val TAG = "MediaDialogFragment"
    }
}