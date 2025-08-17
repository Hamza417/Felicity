package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.LastSongDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MediaFragment : ScopedFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                Log.d(TAG, "Seek position: $position")
                PlayerPreferences.setLastSongSeek(position)
            }
        }
    }

    protected fun setMediaItems(songs: List<Song>, position: Int = 0) {
        PlayerPreferences.setLastSongPosition(position)
        PlayerPreferences.setLastSongId(songs.getOrNull(position)?.id ?: -1L)
        MediaManager.setSongs(songs, position)
        MediaManager.play()
        createSongHistoryDatabase(songs)
    }

    private fun createSongHistoryDatabase(songs: List<Song>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val lastSongDatabase = LastSongDatabase.getInstance(requireContext())
            val songDao = lastSongDatabase.songDao()
            songDao.cleanInsert(songs)
        }
    }

    companion object {
        private const val TAG = "MediaFragment"
    }
}