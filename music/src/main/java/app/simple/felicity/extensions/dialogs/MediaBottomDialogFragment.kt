package app.simple.felicity.extensions.dialogs

import androidx.lifecycle.lifecycleScope
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class MediaBottomDialogFragment : ScopedBottomSheetFragment() {

    protected fun setMediaItems(songs: List<Audio>, position: Int = 0) {
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

    companion object {
        private const val TAG = "MediaDialogFragment"
    }
}