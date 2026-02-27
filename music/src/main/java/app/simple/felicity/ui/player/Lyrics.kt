package app.simple.felicity.ui.player

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.decorations.lrc.view.ModernLrcView
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.dialogs.player.LyricsMenu.Companion.showLyricsMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.ui.player.Lyrics.Companion.TEXT_SIZE_DEBOUNCE_MS
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaFragment() {

    private lateinit var binding: FragmentLyricsBinding

    /** Debounce handler – coalesces rapid text-size changes from the slider. */
    private val textSizeHandler = Handler(Looper.getMainLooper())
    private val textSizeRunnable = Runnable { applyTextSize() }

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
        applyTextSize()
        updateState()

        binding.lrc.setOnLrcClickListener { timeInMillis, _ ->
            MediaManager.seekTo(timeInMillis)
        }

        binding.settings.setOnClickListener {
            childFragmentManager.showLyricsMenu()
        }

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
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

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.toLong().div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.toLong().div(1000))
        }

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    MediaManager.seekTo(progress.toLong())
                }
            }
        })
    }

    private fun setAlignment() {
        when (LyricsPreferences.getLrcAlignment()) {
            LyricsPreferences.LEFT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.LEFT)
            LyricsPreferences.CENTER -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.CENTER)
            LyricsPreferences.RIGHT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.RIGHT)
        }
    }

    /** Applies the current text-size preferences to the view immediately. */
    private fun applyTextSize() {
        val normal = LyricsPreferences.getLrcTextSize()
        binding.lrc.setTextSizes(normal, normal * LRC_HIGHLIGHT_TIMES)
    }

    /**
     * Schedules [applyTextSize] after [TEXT_SIZE_DEBOUNCE_MS] ms, cancelling any
     * previously pending call.  Rapid slider events therefore collapse into one update
     * that fires only once the user stops (or nearly stops) dragging.
     */
    private fun scheduleTextSizeUpdate() {
        textSizeHandler.removeCallbacks(textSizeRunnable)
        textSizeHandler.postDelayed(textSizeRunnable, TEXT_SIZE_DEBOUNCE_MS)
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            binding.play.playing()
        } else {
            binding.play.paused()
        }
    }

    private fun updateState() {
        val audio = MediaManager.getCurrentSong() ?: return
        binding.artist.text = audio.artist
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updatePlayButtonState(MediaManager.isPlaying())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LyricsPreferences.LRC_ALIGNMENT -> setAlignment()
            LyricsPreferences.LRC_TEXT_SIZE -> scheduleTextSizeUpdate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textSizeHandler.removeCallbacks(textSizeRunnable)
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.lrc.updateTime(seek)
        binding.seekbar.setProgress(seek.toFloat(), fromUser = false, animate = true)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        lyricsViewModel.loadLrcData()
        binding.name.text = audio.title
        binding.artist.text = audio.artist
        binding.seekbar.setMaxWithReset(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> {
                updatePlayButtonState(true)
            }
            MediaConstants.PLAYBACK_PAUSED -> {
                updatePlayButtonState(false)
            }
        }
    }

    companion object {
        fun newInstance(): Lyrics {
            val args = Bundle()
            val fragment = Lyrics()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "LyricsFragment"

        private const val LRC_HIGHLIGHT_TIMES = 1.2F

        /** How long to wait after the last slider event before applying the text-size change. */
        private const val TEXT_SIZE_DEBOUNCE_MS = 150L
    }
}

