package app.simple.felicity.ui.panels

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
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentLyricsBinding
import app.simple.felicity.decorations.lrc.view.ModernLrcView
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.dialogs.lyrics.LyricsMenu
import app.simple.felicity.dialogs.lyrics.LyricsMenu.Companion.showLyricsMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTypeWriting
import app.simple.felicity.ui.panels.Lyrics.Companion.TEXT_SIZE_DEBOUNCE_MS
import app.simple.felicity.viewmodels.player.LyricsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaFragment() {

    private lateinit var binding: FragmentLyricsBinding

    /**
     * Path of the song whose lyrics are currently rendered in the lrc view.
     * Compared in [onAudio] to distinguish a genuine song change from a
     * predictive-back resume that replays [MediaManager.songPositionFlow] for
     * the same song — the latter must NOT reset the view.
     *
     * @author Hamza417
     */
    private var currentAudioPath: String? = null

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
        binding.lrc.setEmptyText(getString(R.string.no_lyrics_found))

        binding.lrc.setOnLrcClickListener { timeInMillis, _ ->
            MediaManager.seekTo(timeInMillis)
        }

        binding.settings.setOnClickListener {
            childFragmentManager.showLyricsMenu().setOnMenuListener(object : LyricsMenu.Companion.LyricsMenuListener {
                override fun onTimeMinusClicked() {
                    lyricsViewModel.seekBy(-SEEK_JUMP_MS)
                    lyricsViewModel.syncOffset -= SEEK_JUMP_MS
                }

                override fun onTimePlusClicked() {
                    lyricsViewModel.seekBy(SEEK_JUMP_MS)
                    lyricsViewModel.syncOffset += SEEK_JUMP_MS
                }

                override fun onLyricsDelete() {
                    showDeleteConfirmation {
                        if (it) {
                            lyricsViewModel.deleteLrc {
                                binding.lrc.reset()
                                Log.d(TAG, "Lyrics deleted successfully.")
                            }
                        }
                    }
                }
            })
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
            } else {
                Log.d(TAG, "Loaded lyrics with ${lrcData.size()} lines.")
                binding.lrc.updateLrcDataInPlace(
                        lrcData, MediaManager.getSeekPosition() + lyricsViewModel.syncOffset)
            }
        }

        // Sync offset changed: just nudge updateTime — LrcData and scroll stay untouched
        lyricsViewModel.getSyncOffsetMs().observe(viewLifecycleOwner) { offset ->
            Log.d(TAG, "Sync offset updated: ${offset}ms")
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

    private fun setAlignment(animate: Boolean = false) {
        when (LyricsPreferences.getLrcAlignment()) {
            LyricsPreferences.LEFT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.LEFT, animate)
            LyricsPreferences.CENTER -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.CENTER, animate)
            LyricsPreferences.RIGHT -> binding.lrc.setTextAlignment(ModernLrcView.Alignment.RIGHT, animate)
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
        binding.name.text = audio.title
        binding.artist.text = audio.artist
        binding.lrc.setDuration(audio.duration)
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updatePlayButtonState(MediaManager.isPlaying())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LyricsPreferences.LRC_ALIGNMENT -> setAlignment(animate = true)
            LyricsPreferences.LRC_TEXT_SIZE -> scheduleTextSizeUpdate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textSizeHandler.removeCallbacks(textSizeRunnable)
    }

    override fun onResume() {
        super.onResume()
        // If lyrics data is missing after returning from a long background session,
        // force a fresh reload so the view is not left blank.
        if (lyricsViewModel.getLrcData().value?.isEmpty == true) {
            lyricsViewModel.reloadLrcData()
        }
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.lrc.updateTime(seek + lyricsViewModel.syncOffset)
        binding.seekbar.setProgress(seek.toFloat(), fromUser = false, animate = true)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)

        val isSameSong = audio.path == currentAudioPath
        currentAudioPath = audio.path

        if (!isSameSong) {
            // Real song change — reset the view and kick off a fresh lyrics load.
            binding.lrc.reset()
            lyricsViewModel.loadLrcData()
            binding.name.setTypeWriting(audio.title ?: getString(R.string.unknown))
            binding.artist.setTypeWriting(audio.artist ?: getString(R.string.unknown))
            binding.lrc.setDuration(audio.duration)
            binding.seekbar.setMaxWithReset(audio.duration.toFloat())
        }

        // Always refresh the seek position (covers predictive-back resume and actual changes).
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
        private const val SEEK_JUMP_MS = 500L
    }
}