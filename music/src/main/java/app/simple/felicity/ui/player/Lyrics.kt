package app.simple.felicity.ui.player

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.decorations.lrc.view.ModernLrcView
import app.simple.felicity.dialogs.player.LyricsMenu.Companion.showLyricsMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaFragment() {

    private lateinit var binding: FragmentLyricsBinding

    private val lyricsViewModel: LyricsViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<LyricsViewModel.Factory> {
                    it.create(audio = null)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLyricsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()
        setAlignment()

        binding.lrc.setOnLrcClickListener { timeInMillis, _ ->
            MediaManager.seekTo(timeInMillis)
        }

        binding.settings.setOnClickListener {
            childFragmentManager.showLyricsMenu()
        }

        lyricsViewModel.getLrcData().observe(viewLifecycleOwner) { lrcData ->
            if (lrcData.isEmpty) {
                Log.d(TAG, "No lyrics found for the current song.")
                binding.lrc.reset()
            } else {
                binding.lrc.setLrcData(lrcData)
                binding.lrc.updateTime(MediaManager.getSeekPosition())
            }
        }
    }

    private fun setAlignment() {
        when (LyricsPreferences.getLrcAlignment()) {
            LyricsPreferences.LEFT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.LEFT)
            LyricsPreferences.CENTER -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.CENTER)
            LyricsPreferences.RIGHT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.RIGHT)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LyricsPreferences.LRC_ALIGNMENT -> {
                setAlignment()
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