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
import app.simple.felicity.decorations.seekbars.WaveformSeekbar
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.dialogs.lyrics.AddLyrics
import app.simple.felicity.dialogs.lyrics.AddLyrics.Companion.showAddLyrics
import app.simple.felicity.dialogs.lyrics.LyricsMenu
import app.simple.felicity.dialogs.lyrics.LyricsMenu.Companion.showLyricsMenu
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.ui.panels.Lyrics.Companion.TEXT_SIZE_DEBOUNCE_MS
import app.simple.felicity.viewmodels.player.LyricsViewModel
import app.simple.felicity.viewmodels.player.WaveformViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class Lyrics : MediaFragment(), AddLyrics.Companion.OnLyricsCreatedListener {

    private lateinit var binding: FragmentLyricsBinding

    /**
     * Path of the song whose lyrics are currently rendered in the lrc view.
     * Compared in [onAudio] to distinguish a genuine song change from a
     * predictive-back resume that replays [MediaPlaybackManager.songPositionFlow] for
     * the same song — the latter must NOT reset the view.
     *
     * Also used to re-seek seekbar and lrc view to the correct position when a predictive-back resumes
     */
    private var currentAudioPath: String? = null

    /**
     * Holds an [Audio] track whose waveform has been requested but whose load has been
     * intentionally deferred because ExoPlayer has not yet reached the playing state.
     * Cleared to `null` as soon as [loadWaveformWhenReady] actually kicks off the decode.
     */
    private var pendingWaveformAudio: Audio? = null

    /** Debounce handler – coalesces rapid text-size changes from the slider. */
    private val textSizeHandler = Handler(Looper.getMainLooper())
    private val textSizeRunnable = Runnable { applyTextSize() }

    /** ViewModel that decodes and exposes the per-second waveform amplitude data. */
    private val waveformViewModel: WaveformViewModel by viewModels()

    /**
     * Scoped to the activity so that [LyricsSearch] can obtain the same instance and call
     * [LyricsViewModel.reloadLrcData] after saving a new sidecar file, causing this fragment's
     * observer to pick up the updated lyrics without requiring a full close/reopen cycle.
     */
    private val lyricsViewModel: LyricsViewModel by viewModels(
            ownerProducer = { this },
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
            MediaPlaybackManager.seekTo(timeInMillis)
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
                    withSureDialog { sure ->
                        if (sure) {
                            lyricsViewModel.deleteLrc {
                                binding.lrc.reset()
                                Log.d(TAG, "Lyrics deleted successfully.")
                            }
                        }
                    }
                }

                override fun onLrcEdit() {
                    val currentAudio = MediaPlaybackManager.getCurrentSong() ?: return
                    openFragment(LrcEditor.newInstance(currentAudio), LrcEditor.TAG)
                }
            })
        }

        binding.next.setOnClickListener {
            MediaPlaybackManager.next()
        }

        binding.previous.setOnClickListener {
            MediaPlaybackManager.previous()
        }

        binding.play.setOnClickListener {
            MediaPlaybackManager.flipState()
        }

        binding.search.setOnClickListener {
            openFragment(LyricsSearch.newInstance(), LyricsSearch.TAG)
        }

        binding.addLyrics?.setOnClickListener {
            val currentAudio = MediaPlaybackManager.getCurrentSong() ?: return@setOnClickListener
            childFragmentManager.showAddLyrics(currentAudio)
                .setOnLyricsCreatedListener(this)
        }

        lyricsViewModel.getLrcData().observe(viewLifecycleOwner) { lrcData ->
            if (lrcData.isEmpty) {
                Log.d(TAG, "No lyrics found for the current song.")
            } else {
                Log.d(TAG, "Loaded lyrics with ${lrcData.size()} lines.")
                // Use setLrcDataWithPosition so scroll is snapped only after the first draw pass,
                // when layout heights are fully populated. This prevents the highlight being placed
                // at a wrong (fallback-size based) offset when the cache is empty after a reset().
                binding.lrc.setLrcDataWithPosition(
                        lrcData, MediaPlaybackManager.getSeekPosition() + lyricsViewModel.syncOffset)
            }
        }

        // Listen for the signal from LyricsSearch that a new .lrc sidecar was saved so that
        // the lyrics panel refreshes immediately without requiring a close/reopen cycle.
        parentFragmentManager.setFragmentResultListener(
                LyricsSearch.REQUEST_KEY_LYRICS_SAVED, viewLifecycleOwner) { _, _ ->
            Log.d(TAG, "Lyrics saved signal received, reloading lrc data.")
            lyricsViewModel.reloadLrcData()
        }

        // Listen for the signal from LrcEditor that the user has saved a newly edited .lrc
        // file, so this panel can reload without requiring the user to close and reopen.
        parentFragmentManager.setFragmentResultListener(
                LrcEditor.REQUEST_KEY_LRC_SAVED, viewLifecycleOwner) { _, _ ->
            Log.d(TAG, "LRC editor saved signal received, reloading lrc data.")
            lyricsViewModel.reloadLrcData()
        }

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.div(1000))
        }

        binding.seekbar.setOnSeekListener(object : WaveformSeekbar.OnSeekListener {
            override fun onSeekEnd(positionMs: Long) {
                MediaPlaybackManager.seekTo(positionMs)
            }
        })

        binding.seekbar.setOnFlingEndListener { positionMs ->
            MediaPlaybackManager.seekTo(positionMs)
        }

        waveformViewModel.getWaveformData().observe(viewLifecycleOwner) { amplitudes ->
            binding.seekbar.setAmplitudes(amplitudes)
        }
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
        val audio = MediaPlaybackManager.getCurrentSong() ?: return
        currentAudioPath = audio.path
        binding.name.text = audio.title
        binding.artist.text = audio.getArtists()
        binding.lrc.setDuration(audio.duration)
        binding.seekbar.setDuration(audio.duration)
        binding.seekbar.setProgress(MediaPlaybackManager.getSeekPosition(), animate = false)
        updatePlayButtonState(MediaPlaybackManager.isPlaying())

        // Defer waveform decoding until ExoPlayer is actually playing to avoid
        // Amplituda and ExoPlayer fighting over the same file I/O resources.
        loadWaveformWhenReady(audio)
    }

    /**
     * Schedules waveform decoding for [audio].
     *
     * The decode starts immediately when the player is already playing or in a ready state
     * (paused or playing). If the player is still buffering or idle, [audio] is stored in
     * [pendingWaveformAudio] and the decode is deferred until [onPlaybackStateChanged]
     * receives [MediaConstants.PLAYBACK_READY] or [MediaConstants.PLAYBACK_PLAYING].
     *
     * This prevents Amplituda and ExoPlayer from contending over the same file I/O
     * resources during the initial buffering phase.
     *
     * @param audio the track whose waveform should be decoded
     */
    private fun loadWaveformWhenReady(audio: Audio) {
        if (MediaPlaybackManager.isPlaying() || MediaPlaybackManager.isPlayerReady()) {
            waveformViewModel.loadWaveform(audio)
            pendingWaveformAudio = null
        } else {
            pendingWaveformAudio = audio
        }
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

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
        binding.seekbar.setProgress(seek, animate = true)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)

        val isSameSong = audio.path == currentAudioPath
        currentAudioPath = audio.path

        if (!isSameSong) {
            // Real song change — reset the view and kick off a fresh lyrics load.
            binding.lrc.reset()
            lyricsViewModel.loadLrcData()
            val forward = MediaPlaybackManager.lastNavigationDirection
            binding.name.setTextWithEffect(audio.title ?: getString(R.string.unknown), forward)
            binding.artist.setTextWithEffect(audio.getArtists(), forward, 50L)
            binding.lrc.setDuration(audio.duration)
            binding.seekbar.setDurationWithReset(audio.duration)
        }

        // Always refresh the seek position (covers predictive-back resume and actual changes).
        binding.seekbar.setProgress(MediaPlaybackManager.getSeekPosition(), animate = true)

        // Defer waveform decoding until ExoPlayer is actually playing to avoid
        // Amplituda and ExoPlayer fighting over the same file I/O resources.
        loadWaveformWhenReady(audio)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        when (state) {
            MediaConstants.PLAYBACK_READY -> {
                // ExoPlayer has finished buffering and the decoder is ready. This is the
                // earliest safe moment to run Amplituda's extraction in parallel, regardless
                // of whether playback will start immediately or stay paused.
                pendingWaveformAudio?.let { audio ->
                    waveformViewModel.loadWaveform(audio)
                    pendingWaveformAudio = null
                }
            }
            MediaConstants.PLAYBACK_PLAYING -> {
                updatePlayButtonState(true)
                // Also drain any pending waveform that was queued before the ready event arrived
                // (e.g., when the service emits PLAYING without a preceding READY being observed).
                pendingWaveformAudio?.let { audio ->
                    waveformViewModel.loadWaveform(audio)
                    pendingWaveformAudio = null
                }
            }
            MediaConstants.PLAYBACK_PAUSED -> {
                updatePlayButtonState(false)
            }
        }
    }

    override fun onLyricsCreated() {
        // Reload the lyrics view after the sidecar file has been created
        lyricsViewModel.reloadLrcData()
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