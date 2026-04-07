package app.simple.felicity.extensions.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.decorations.helpers.SwipeDownToCloseListener
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.pager.ImagePageAdapter
import app.simple.felicity.decorations.seekbars.WaveformSeekbar
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithFade
import app.simple.felicity.decorations.views.FavoriteButton
import app.simple.felicity.decorations.views.FelicityVisualizer
import app.simple.felicity.decorations.views.FlipPlayPauseView
import app.simple.felicity.dialogs.app.AudioPipelineDialog.Companion.showAudioPipeline
import app.simple.felicity.dialogs.player.VisualizerConfig.Companion.showVisualizerConfig
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.engine.utils.PcmInfoFormatter
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.ui.panels.Equalizer
import app.simple.felicity.ui.panels.Lyrics
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Search
import app.simple.felicity.viewmodels.player.WaveformViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

/**
 * Abstract base fragment that provides all common player UI logic shared between
 * player interface variants. Concrete subclasses supply their own layout by
 * implementing [onCreateView] and providing each required view through the
 * abstract view properties declared below.
 *
 * The base class handles waveform loading, visualizer wiring, seekbar updates,
 * repeat mode toggling, favorite toggling, and all album-art pager synchronization.
 * Subclasses only need to inflate their specific layout and wire the abstract
 * properties to the corresponding ViewBinding fields.
 *
 * @author Hamza417
 */
abstract class BasePlayerFragment : MediaFragment() {

    private var imagePageAdapter: ImagePageAdapter? = null
    private var swipeDownListener: SwipeDownToCloseListener? = null

    /** ViewModel that decodes and exposes the per-second waveform amplitude data. */
    private val waveformViewModel: WaveformViewModel by viewModels()

    /**
     * Holds an [Audio] track whose waveform has been requested but whose load has been
     * intentionally deferred because ExoPlayer has not yet reached the playing state.
     * Cleared to `null` as soon as [loadWaveformWhenReady] actually kicks off the decode.
     */
    private var pendingWaveformAudio: Audio? = null

    /**
     * Tracks the ID of the last song for which [onAudio] performed a full seekbar reset.
     * Used to distinguish a genuine song change from a lifecycle re-emission (e.g., a
     * predictive back gesture that is cancelled), so that [setDurationWithReset] is not
     * called again for the same song and the seekbar does not reanimate from position 0.
     */
    private var lastLoadedAudioId: Long = -1L

    /** The pager that cycles through album art pages. */
    protected abstract val pager: FelicityPager

    /** The text view showing the current queue position (e.g. "3/12"). */
    protected abstract val count: TextView

    /** The animated play/pause toggle button. */
    protected abstract val play: FlipPlayPauseView

    /** The button that opens the playing queue panel. */
    protected abstract val queue: View

    /** The button that opens the search panel. */
    protected abstract val search: View

    /** The button that opens the overflow / song menu dialog. */
    protected abstract val menu: View

    /** The repeat mode indicator and toggle button. */
    protected abstract val repeat: ImageView

    /** The text view that shows PCM format info for the current track. */
    protected abstract val pcmInfo: TextView

    /** The waveform seekbar that shows playback progress. */
    protected abstract val seekbar: WaveformSeekbar

    /** The button that navigates to the lyrics panel. */
    protected abstract val lyrics: View

    /** The animated heart button that toggles the favorite state. */
    protected abstract val favorite: FavoriteButton

    /** The button that opens the equalizer panel. */
    protected abstract val equalizer: View

    /** The button that opens the Milkdrop visualizer panel. */
    protected abstract val visualizerButton: View

    /** The in-player FFT visualizer overlay. */
    protected abstract val visualizer: FelicityVisualizer

    /** The fast-forward / next track button. */
    protected abstract val next: ImageButton

    /** The rewind / previous track button. */
    protected abstract val previous: ImageButton

    /** The text view that shows the track title. */
    protected abstract val title: TextView

    /** The text view that shows the artist name(s). */
    protected abstract val artist: TextView

    /** The text view that shows the album name. */
    protected abstract val album: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeDownListener = SwipeDownToCloseListener(this, requireView())

        requireView().setOnTouchListener(swipeDownListener)
        requireHiddenMiniPlayer()
        updateState()
        setVisualizerState()

        // Mirror swipe-down-to-close behavior on the album art pager so that a downward
        // swipe on the cover image dismisses the player, exactly like swiping on any other
        // area of the screen.
        //
        // The pager consumes ACTION_DOWN, so SwipeDownToCloseListener never sees it and its
        // isDragging flag stays false, causing every forwarded ACTION_MOVE to be silently
        // dropped. passExternalDrag() bootstraps initialY + isDragging on the first call
        // using the reconstructed drag-start position, and endExternalDrag() runs the
        // dismiss-or-snap-back logic that ACTION_UP would normally trigger.
        pager.setOnVerticalDragListener(object : FelicityPager.OnVerticalDragListener {
            override fun onVerticalDrag(totalDeltaY: Float, event: MotionEvent) {
                // event.rawY - totalDeltaY reconstructs the raw Y at gesture start.
                swipeDownListener?.passExternalDrag(event, event.rawY - totalDeltaY)
            }

            override fun onVerticalDragEnd(totalDeltaY: Float, velocityY: Float, event: MotionEvent) {
                swipeDownListener?.endExternalDrag(event.rawY - totalDeltaY)
            }
        })

        pager.setAdapter(
                ImagePageAdapter(
                        count = MediaPlaybackManager.getSongs().size,
                        provider = { pos, iv ->
                            val audio = MediaPlaybackManager.getSongs()[pos]
                            iv.loadArtCover(audio,
                                            shadow = false,
                                            crop = true,
                                            roundedCorners = false,
                                            blur = false,
                                            greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                                            darken = false)
                        },
                        canceller = { iv ->
                            Glide.with(iv).clear(iv)
                        }
                ).also { imagePageAdapter = it },
        )

        // Jump to the currently playing song immediately after the adapter is set.
        // Using smoothScroll=false so the correct page (and its cover art) is shown
        // from the very first frame, even when position == 0.
        val initialPosition = MediaPlaybackManager.getCurrentSongPosition()
        pager.setCurrentItem(initialPosition, smoothScroll = false)
        count.text = buildString {
            append(initialPosition + 1)
            append("/")
            append(MediaPlaybackManager.getSongs().size)
        }

        pager.addOnPageChangeListener(object : FelicityPager.OnPageChangeListener {
            override fun onPageSelected(position: Int, fromUser: Boolean) {
                super.onPageSelected(position, fromUser)
                if (fromUser) {
                    MediaPlaybackManager.updatePosition(position)
                }
            }
        })

        next.setupFastForwardButton()
        previous.setupFastRewindButton()

        play.setOnClickListener {
            MediaPlaybackManager.flipState()
        }

        queue.setOnClickListener {
            openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
        }

        count.setOnClickListener {
            queue.callOnClick()
        }

        search.setOnClickListener {
            openFragment(Search.newInstance(), Search.TAG)
        }

        menu.setOnClickListener {
            when (UserInterfacePreferences.getPlayerInterface()) {
                UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT -> {
                    openSongsMenu(
                            audios = MediaPlaybackManager.getSongs(),
                            position = MediaPlaybackManager.getCurrentSongPosition(),
                            imageView = pager.getCurrentImageView()
                    )
                }
                UserInterfacePreferences.PLAYER_INTERFACE_FADED -> {
                    openSongsMenu(
                            audios = MediaPlaybackManager.getSongs(),
                            position = MediaPlaybackManager.getCurrentSongPosition(),
                            imageView = null // No shared element transition on the faded player variant
                    )
                }
            }
        }

        repeat.setOnClickListener {
            val current = PlayerPreferences.getRepeatMode()
            val next = when (current) {
                MediaConstants.REPEAT_OFF -> MediaConstants.REPEAT_QUEUE
                MediaConstants.REPEAT_QUEUE -> MediaConstants.REPEAT_ONE
                else -> MediaConstants.REPEAT_OFF
            }
            PlayerPreferences.setRepeatMode(next)
            updateRepeatButtonIcon(next)
        }

        pcmInfo.setOnClickListener {
            PlayerPreferences.setPcmInfoMode((PlayerPreferences.getPcmInfoMode() + 1) % 3)
        }

        pcmInfo.setOnLongClickListener {
            showAudioPipeline(anchorView = pcmInfo)
            true
        }

        // Observe repeat mode changes from the service (e.g. on startup).
        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.repeatModeFlow.collect { repeatMode ->
                updateRepeatButtonIcon(repeatMode)
            }
        }

        // Set initial icon based on saved preference.
        updateRepeatButtonIcon(PlayerPreferences.getRepeatMode())

        seekbar.setOnSeekListener(object : WaveformSeekbar.OnSeekListener {
            override fun onSeekEnd(positionMs: Long) {
                MediaPlaybackManager.seekTo(positionMs)
            }
        })

        seekbar.setOnFlingEndListener { positionMs ->
            MediaPlaybackManager.seekTo(positionMs)
        }

        seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress / 1000L)
        }

        seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max / 1000L)
        }

        // Observe waveform amplitude data from the ViewModel.
        waveformViewModel.getWaveformData().observe(viewLifecycleOwner) { amplitudes ->
            seekbar.setAmplitudes(amplitudes)
        }

        lyrics.setOnClickListener {
            openFragment(Lyrics.newInstance(), Lyrics.TAG)
        }

        favorite.setOnClickListener {
            toggleFavorite()
        }

        equalizer.setOnClickListener {
            openFragment(Equalizer.newInstance(), Equalizer.TAG)
        }

        visualizerButton.setOnClickListener {
            childFragmentManager.showVisualizerConfig()
        }
    }

    private fun setVisualizerState() {
        if (PlayerPreferences.isVisualizerEnabled()) {
            // Wire the visualizer view's twin buffers directly to the audio processor so the
            // audio thread can write FFT magnitudes without any intermediate coroutine hop.
            // setDirectOutput is a no-op when the processor is not yet available (service not
            // started), but in practice the service starts before this fragment is shown.
            VisualizerManager.processor?.setDirectOutput(
                    visualizer.bufferA,
                    visualizer.bufferB,
                    visualizer.isBufferAFront,
                    visualizer
            )
        } else {
            VisualizerManager.processor?.clearDirectOutput()
        }

        visualizer.visibility = if (PlayerPreferences.isVisualizerEnabled()) View.VISIBLE else View.GONE
    }

    private fun updateState() {
        val audio = MediaPlaybackManager.getCurrentSong() ?: return
        lastLoadedAudioId = audio.id
        title.text = audio.title ?: getString(R.string.unknown)
        artist.text = audio.getArtists()
        album.text = audio.album ?: getString(R.string.unknown)
        pcmInfo.text = PcmInfoFormatter.formatPcmInfo(audio)
        seekbar.setDuration(audio.duration)
        seekbar.setProgress(MediaPlaybackManager.getSeekPosition(), animate = false)
        updatePlayButtonState(MediaPlaybackManager.isPlaying())
        updateFavoriteIcon(audio)

        // Defer waveform decoding until ExoPlayer is actually playing to avoid
        // Amplituda and ExoPlayer fighting over the same file I/O resources.
        loadWaveformWhenReady(audio)
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            play.playing()
        } else {
            play.paused()
        }
    }

    private fun updateRepeatButtonIcon(repeatMode: Int) {
        when (repeatMode) {
            MediaConstants.REPEAT_ONE -> {
                repeat.setImageResource(R.drawable.ic_repeat_one)
                repeat.alpha = 1f
            }
            MediaConstants.REPEAT_QUEUE -> {
                repeat.setImageResource(R.drawable.ic_repeat)
                repeat.alpha = 1f
            }
            else -> { // REPEAT_OFF
                repeat.setImageResource(R.drawable.ic_repeat)
                repeat.alpha = 0.4f
            }
        }
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

    override fun onDestroyView() {
        // Release the direct twin-buffer connection so the audio thread no longer holds
        // a WeakReference to the now-destroyed visualizer view.
        VisualizerManager.processor?.clearDirectOutput()
        super.onDestroyView()
        imagePageAdapter = null
    }

    override fun onSongListChanged(songs: List<Audio>) {
        super.onSongListChanged(songs)
        val adapter = imagePageAdapter ?: return
        val currentPos = MediaPlaybackManager.getCurrentSongPosition()
        adapter.updateCount(songs.size)
        pager.notifyDataSetChanged()
        // Keep the pager on the correct page after the list shrinks or reorders.
        pager.setCurrentItem(currentPos, smoothScroll = false)
        count.text = buildString {
            append(currentPos + 1)
            append("/")
            append(songs.size)
        }
    }

    override fun onPositionChanged(position: Int) {
        super.onPositionChanged(position)
        Log.i(LOG_TAG, "Position changed to $position")
        // Never move the pager while the user's finger is on it — that would fight the gesture.
        if (pager.currentScrollState != FelicityPager.SCROLL_STATE_DRAGGING) {
            if (pager.getCurrentItem() != position) {
                pager.setCurrentItem(position, true)
            }
        }
        count.text = buildString {
            append(position + 1)
            append("/")
            append(MediaPlaybackManager.getSongs().size)
        }
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        val forward = MediaPlaybackManager.lastNavigationDirection

        val isSameSong = audio.id == lastLoadedAudioId

        if (!isSameSong) {
            // Genuine song change: update text with the directional transition effect and
            // trigger the seekbar left-bar fade before the new waveform data arrives.
            lastLoadedAudioId = audio.id
            title.setTextWithEffect(audio.title ?: getString(R.string.unknown), forward)
            artist.setTextWithEffect(audio.getArtists(), forward, 50L)
            album.setTextWithEffect(audio.album ?: getString(R.string.unknown), forward, 100L)
            pcmInfo.text = PcmInfoFormatter.formatPcmInfo(audio)
            seekbar.setDurationWithReset(audio.duration)
        }

        // Always sync seek position. For a same-song re-emission (e.g., predictive back
        // cancel) this is the only seekbar call needed — no reset, no animation from zero.
        seekbar.setProgress(MediaPlaybackManager.getSeekPosition(), animate = true)
        updateFavoriteIcon(audio)

        // Defer waveform decoding until ExoPlayer is actually playing to avoid
        // Amplituda and ExoPlayer fighting over the same file I/O resources.
        loadWaveformWhenReady(audio)
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        seekbar.setProgress(seek, animate = true)
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

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    /**
     * Updates the favorite button icon from the [Audio] model's [Audio.isFavorite] field.
     * No database query required — the model is the source of truth.
     */
    private fun updateFavoriteIcon(audio: Audio) {
        favorite.setFavorite(audio.isFavorite, animate = true)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            PlayerPreferences.VISUALIZER_ENABLED -> {
                setVisualizerState()
            }
            PlayerPreferences.WAVEFORM_MODE -> {
                seekbar.waveformMode = PlayerPreferences.getWaveformMode()
            }
            PlayerPreferences.PCM_INFO_MODE -> {
                val audio = MediaPlaybackManager.getCurrentSong() ?: return
                pcmInfo.setTextWithFade(PcmInfoFormatter.formatPcmInfo(audio))
            }
        }
    }

    companion object {
        /** Back-stack tag shared by all player interface variants. */
        const val TAG = "BasePlayer"
        private const val LOG_TAG = "BasePlayerFragment"
    }
}

