package app.simple.felicity.ui.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Lyrics : MediaFragment() {

    private lateinit var binding: FragmentLyricsBinding

    private val lyricsViewModel: LyricsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLyricsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        binding.lrc.setOnLrcClickListener { timeInMillis, _ ->
            MediaManager.seekTo(timeInMillis)
        }

        lyricsViewModel.getLrcData().observe(viewLifecycleOwner) { lrcData ->
            if (lrcData.isEmpty) {
                Log.d(TAG, "No lyrics found for the current song.")
                binding.lrc.reset()
            } else {
                binding.lrc.setLrcData(lrcData)
            }
        }
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.lrc.updateTime(seek)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        lyricsViewModel.loadLrcData()
        binding.name.text = audio.title
        binding.artist.text = audio.artist
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