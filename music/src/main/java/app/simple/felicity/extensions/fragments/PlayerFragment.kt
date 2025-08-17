package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.managers.MediaManager
import kotlinx.coroutines.launch

abstract class PlayerFragment : MediaFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                PlayerPreferences.setLastSongSeek(position)
                onProgress(position, MediaManager.getDuration())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    abstract fun onServiceConnected()
    abstract fun onServiceDisconnected()
    abstract fun onPrepared()
    abstract fun onMetaData()
    abstract fun onQuitMusicService()
    abstract fun onStateChanged(isPlaying: Boolean)
    abstract fun onNext()
    abstract fun onPrevious()
    abstract fun onBuffering(progress: Int)
    abstract fun onMediaError(error: String)
    abstract fun onProgress(progress: Long, duration: Long)
}
