package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.LastSongDatabase
import app.simple.felicity.repository.database.instances.SongStatDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.utils.SongUtils
import app.simple.felicity.repository.utils.SongUtils.createSongStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MediaFragment : ScopedFragment() {

    private val miniPlayerCallbacks: MiniPlayerCallbacks?
        get() = requireActivity() as? MiniPlayerCallbacks

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                PlayerPreferences.setLastSongSeek(position)
                onSeekChanged(position)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songPositionFlow.collect { position ->
                Log.d(TAG, "Song position: $position")
                PlayerPreferences.setLastSongPosition(position)
                MediaManager.getCurrentSong()?.let { song ->
                    onSong(song)
                }
                onPositionChanged(position)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songListFlow.collect { songs ->
                Log.d(TAG, "Song list updated: ${songs.size} songs")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.playbackStateFlow.collect { state ->
                onPlaybackStateChanged(state)
            }
        }
    }

    protected fun setMediaItems(songs: List<Song>, position: Int = 0) {
        PlayerPreferences.setLastSongPosition(position)
        PlayerPreferences.setLastSongId(songs.getOrNull(position)?.id ?: -1L)
        MediaManager.setSongs(songs, position)
        MediaManager.play()
        createSongHistoryDatabase(songs)
        createStatForSong(songs[position])
    }

    private fun createSongHistoryDatabase(songs: List<Song>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val lastSongDatabase = LastSongDatabase.getInstance(requireContext())
            val songDao = lastSongDatabase.songDao()
            songDao.cleanInsert(songs)
        }
    }

    private fun createStatForSong(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val songStatDatabase = SongStatDatabase.getInstance(requireContext())
            val songStatDao = songStatDatabase.songStatDao()
            val existingStat = songStatDao.getSongStatByStableId(SongUtils.generateStableId(song))

            if (existingStat == null) {
                songStatDao.insertSongStat(song.createSongStat(existingStat))
                Log.d(TAG, "Created new song stat for: ${song.title}")
            } else {
                songStatDao.updateSongStat(existingStat.copy(playCount = existingStat.playCount + 1))
            }
        }
    }

    protected fun requireHiddenMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                miniPlayerCallbacks?.onHideMiniPlayer()
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                miniPlayerCallbacks?.onShowMiniPlayer()
            }
        })
    }

    open fun onPlaybackStateChanged(state: Int) {
        Log.d(TAG, "Playback state changed: $state")
    }

    open fun onSong(song: Song) {
        Log.d(TAG, "New song played: ${song.title} by ${song.artist}")
    }

    open fun onPositionChanged(position: Int) {
        Log.d(TAG, "Position changed: $position")
    }

    open fun onSeekChanged(seek: Long) {
        Log.d(TAG, "Seek changed: $seek")
    }

    companion object {
        private const val TAG = "MediaFragment"
    }
}