package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.decorations.lrc.LrcHelper
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.utils.FileUtils.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Lyrics : MediaFragment() {

    private lateinit var binding: FragmentLyricsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLyricsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()
    }

    private fun setupLyrics() {
        lifecycleScope.launch(Dispatchers.IO) {
            val path = MediaManager.getCurrentSong()?.path?.replaceAfterLast(".", "lrc")?.toFile()
            val lrc = LrcHelper.parseLrcFromFile(path)

            withContext(Dispatchers.Main) {
                binding.lrc.setLrcData(lrc)
            }
        }
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.lrc.updateTime(seek)
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        setupLyrics()
        binding.name.text = song.title
        binding.artist.text = song.artist
    }

    companion object {
        fun newInstance(): Lyrics {
            val args = Bundle()
            val fragment = Lyrics()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "LyricsFragment"
    }
}