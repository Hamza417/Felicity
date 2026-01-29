package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
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

        // Set up tap listener for seeking
        binding.lrc.setOnLrcClickListener { timeInMillis, _ ->
            // Seek to the tapped line's timestamp
            MediaManager.seekTo(timeInMillis)
        }
    }

    private fun setupLyrics() {
        lifecycleScope.launch(Dispatchers.IO) {
            val path = MediaManager.getCurrentSong()?.path?.replaceAfterLast(".", "lrc")?.toFile()

            try {
                if (path?.exists() == true) {
                    val lrcContent = path.readText()
                    val parser = LrcParser()
                    val lrcData = parser.parse(lrcContent)

                    withContext(Dispatchers.Main) {
                        binding.lrc.setLrcData(lrcData)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.lrc.setEmptyText("No lyrics file found")
                    }
                }
            } catch (e: LyricsParseException) {
                withContext(Dispatchers.Main) {
                    binding.lrc.setEmptyText("Failed to parse lyrics")
                }
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