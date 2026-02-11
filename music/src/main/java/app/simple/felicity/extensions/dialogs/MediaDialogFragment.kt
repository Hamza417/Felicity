package app.simple.felicity.extensions.dialogs

import android.util.Log
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.database.instances.SongStatDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.utils.SongUtils
import app.simple.felicity.repository.utils.SongUtils.createSongStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class MediaDialogFragment : ScopedBottomSheetFragment() {

    protected fun setMediaItems(songs: List<Audio>, position: Int = 0) {
        PlayerPreferences.setLastSongPosition(position)
        PlayerPreferences.setLastSongId(songs.getOrNull(position)?.id ?: -1L)
        // MediaManager.setSongs(songs, position)
        MediaManager.play()
        createSongHistoryDatabase(songs)
    }

    private fun createSongHistoryDatabase(songs: List<Audio>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val audioDatabase = AudioDatabase.getInstance(requireContext())
            PlaybackStateManager.savePlaybackState(
                    db = audioDatabase,
                    queueIds = songs.map { it.id },
                    index = MediaManager.getCurrentPosition(),
                    position = MediaManager.getSeekPosition(),
                    shuffle = false,
                    repeat = 0
            )
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

    companion object {
        private const val TAG = "MediaDialogFragment"
    }
}