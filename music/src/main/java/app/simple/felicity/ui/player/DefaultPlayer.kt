package app.simple.felicity.ui.player

import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.decorations.helpers.SwipeDownToCloseListener
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.pager.ImagePageAdapter
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.dialogs.player.VisualizerConfig.Companion.showVisualizerConfig
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.engine.utils.PcmInfoFormatter
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.ui.panels.Equalizer
import app.simple.felicity.ui.panels.Lyrics
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Search
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class DefaultPlayer : MediaFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding
    private var imagePageAdapter: ImagePageAdapter? = null
    private var swipeDownListener: SwipeDownToCloseListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeDownListener = SwipeDownToCloseListener(this, requireView())

        requireView().setOnTouchListener(swipeDownListener)
        requireHiddenMiniPlayer()
        updateState()

        // Mirror swipe-down-to-close behavior on the album art pager so that a downward
        // swipe on the cover image dismisses the player, exactly like swiping on any other
        // area of the screen.
        //
        // The pager consumes ACTION_DOWN, so SwipeDownToCloseListener never sees it and its
        // isDragging flag stays false, causing every forwarded ACTION_MOVE to be silently
        // dropped. passExternalDrag() bootstraps initialY + isDragging on the first call
        // using the reconstructed drag-start position, and endExternalDrag() runs the
        // dismiss-or-snap-back logic that ACTION_UP would normally trigger.
        binding.pager.setOnVerticalDragListener(object : FelicityPager.OnVerticalDragListener {
            override fun onVerticalDrag(totalDeltaY: Float, event: MotionEvent) {
                // event.rawY - totalDeltaY reconstructs the raw Y at gesture start.
                swipeDownListener?.passExternalDrag(event, event.rawY - totalDeltaY)
            }

            override fun onVerticalDragEnd(totalDeltaY: Float, velocityY: Float, event: MotionEvent) {
                swipeDownListener?.endExternalDrag(event.rawY - totalDeltaY)
            }
        })

        binding.pager.setAdapter(
                ImagePageAdapter(
                        count = MediaManager.getSongs().size,
                        provider = { pos, iv ->
                            val audio = MediaManager.getSongs()[pos]
                            iv.loadArtCover(audio,
                                            shadow = false,
                                            crop = true,
                                            roundedCorners = false,
                                            blur = false,
                                            skipCache = false,
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
        val initialPosition = MediaManager.getCurrentPosition()
        binding.pager.setCurrentItem(initialPosition, smoothScroll = false)
        binding.count.text = buildString {
            append(initialPosition + 1)
            append("/")
            append(MediaManager.getSongs().size)
        }

        binding.pager.addOnPageChangeListener(object : FelicityPager.OnPageChangeListener {
            override fun onPageSelected(position: Int, fromUser: Boolean) {
                super.onPageSelected(position, fromUser)
                if (fromUser) {
                    MediaManager.updatePosition(position)
                }
            }
        })

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
        }

        binding.queue.setOnClickListener {
            openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
        }

        binding.count.setOnClickListener {
            binding.queue.callOnClick()
        }

        binding.search.setOnClickListener {
            openFragment(Search.newInstance(), Search.TAG)
        }

        binding.menu.setOnClickListener {
            openSongsMenu(
                    audios = MediaManager.getSongs(),
                    position = MediaManager.getCurrentPosition(),
                    imageView = binding.pager.getCurrentImageView()
            )
        }

        binding.repeat.setOnClickListener {
            val current = PlayerPreferences.getRepeatMode()
            val next = when (current) {
                MediaConstants.REPEAT_OFF -> MediaConstants.REPEAT_QUEUE
                MediaConstants.REPEAT_QUEUE -> MediaConstants.REPEAT_ONE
                else -> MediaConstants.REPEAT_OFF
            }
            PlayerPreferences.setRepeatMode(next)
            updateRepeatButtonIcon(next)
        }

        // Observe repeat mode changes from the service (e.g. on startup)
        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.repeatModeFlow.collect { repeatMode ->
                updateRepeatButtonIcon(repeatMode)
            }
        }

        // Set initial icon based on saved preference
        updateRepeatButtonIcon(PlayerPreferences.getRepeatMode())

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    MediaManager.seekTo(progress.toLong())
                }
            }
        })

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.toLong().div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.toLong().div(1000))
        }

        binding.lyrics.setOnClickListener {
            openFragment(Lyrics.newInstance(), Lyrics.TAG)
        }

        binding.favorite.setOnClickListener {
            toggleFavorite()
        }

        binding.equalizer.setOnClickListener {
            openFragment(Equalizer.newInstance(), Equalizer.TAG)
        }

        binding.visualizerButton.setOnClickListener {
            val newEnabled = !PlayerPreferences.isVisualizerEnabled()
            PlayerPreferences.setVisualizerEnabled(newEnabled)
            updateEqualizerButtonAlpha(newEnabled)
        }

        binding.visualizerButton.setOnLongClickListener {
            childFragmentManager.showVisualizerConfig()
            true
        }

        updateEqualizerButtonAlpha(PlayerPreferences.isVisualizerEnabled())

        // Wire the visualizer view's twin buffers directly to the audio processor so the
        // audio thread can write FFT magnitudes without any intermediate coroutine hop.
        // setDirectOutput is a no-op when the processor is not yet available (service not
        // started), but in practice the service starts before this fragment is shown.
        VisualizerManager.processor?.setDirectOutput(
                binding.visualizer.bufferA,
                binding.visualizer.bufferB,
                binding.visualizer.isBufferAFront,
                binding.visualizer
        )
    }

    private fun updateState() {
        val audio = MediaManager.getCurrentSong() ?: return
        val effect = BehaviourPreferences.getTextChangeEffect()
        val forward = MediaManager.lastNavigationDirection
        binding.title.setTextWithEffect(audio.title ?: getString(R.string.unknown), effect, forward)
        binding.artist.setTextWithEffect(audio.artist ?: getString(R.string.unknown), effect, forward)
        binding.album.setTextWithEffect(audio.album ?: getString(R.string.unknown), effect, forward)
        binding.pcmInfo.text = PcmInfoFormatter.formatPcmInfo(audio)
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updatePlayButtonState(MediaManager.isPlaying())
        updateFavoriteIcon(audio)
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            binding.play.playing()
        } else {
            binding.play.paused()
        }
    }

    private fun updateRepeatButtonIcon(repeatMode: Int) {
        when (repeatMode) {
            MediaConstants.REPEAT_ONE -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat_one)
                binding.repeat.alpha = 1f
            }
            MediaConstants.REPEAT_QUEUE -> {
                binding.repeat.setImageResource(R.drawable.ic_repeat)
                binding.repeat.alpha = 1f
            }
            else -> { // REPEAT_OFF
                binding.repeat.setImageResource(R.drawable.ic_repeat)
                binding.repeat.alpha = 0.4f
            }
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
        val currentPos = MediaManager.getCurrentPosition()
        adapter.updateCount(songs.size)
        binding.pager.notifyDataSetChanged()
        // Keep the pager on the correct page after the list shrinks or reorders.
        binding.pager.setCurrentItem(currentPos, smoothScroll = false)
        binding.count.text = buildString {
            append(currentPos + 1)
            append("/")
            append(songs.size)
        }
    }

    override fun onPositionChanged(position: Int) {
        super.onPositionChanged(position)
        Log.i(TAG, "Position changed to $position")
        // Never move the pager while the user's finger is on it — that would fight the gesture.
        if (binding.pager.currentScrollState != FelicityPager.SCROLL_STATE_DRAGGING) {
            if (binding.pager.getCurrentItem() != position) {
                binding.pager.setCurrentItem(position, true)
            }
        }
        binding.count.text = buildString {
            append(position + 1)
            append("/")
            append(MediaManager.getSongs().size)
        }
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        val effect = BehaviourPreferences.getTextChangeEffect()
        val forward = MediaManager.lastNavigationDirection
        binding.title.setTextWithEffect(audio.title ?: getString(R.string.unknown), effect, forward)
        binding.artist.setTextWithEffect(audio.artist ?: getString(R.string.unknown), effect, forward)
        binding.album.setTextWithEffect(audio.album ?: getString(R.string.unknown), effect, forward)
        binding.pcmInfo.text = PcmInfoFormatter.formatPcmInfo(audio)
        binding.seekbar.setMaxWithReset(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updateFavoriteIcon(audio)
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.seekbar.setProgress(seek.toFloat(), false, animate = true)
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

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    /**
     * Updates the favorite button icon from the [Audio] model's [Audio.isFavorite] field.
     * No database query required — the model is the source of truth.
     */
    private fun updateFavoriteIcon(audio: Audio) {
        binding.favorite.setFavorite(audio.isFavorite, animate = true)
    }

    /**
     * Dims the equalizer/visualizer button when the visualizer is disabled so the user
     * can tell at a glance whether the overlay is currently active.
     *
     * @param enabled `true` when the visualizer is shown; `false` when hidden.
     */
    private fun updateEqualizerButtonAlpha(enabled: Boolean) {
        binding.visualizerButton.alpha = if (enabled) 1f else 0.4f
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            PlayerPreferences.VISUALIZER_ENABLED -> {
                updateEqualizerButtonAlpha(PlayerPreferences.isVisualizerEnabled())
            }
        }
    }

    companion object {
        fun newInstance(): DefaultPlayer {
            val args = Bundle()
            val fragment = DefaultPlayer()
            fragment.arguments = args
            return fragment
        }

        private const val SIZE = 1024

        const val TAG = "DefaultPlayer"
    }
}