package app.simple.felicity.ui.player

import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.pager.ImagePageAdapter
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTypeWriting
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.panels.Equalizer
import app.simple.felicity.ui.panels.Lyrics
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Search
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class DefaultPlayer : MediaFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding
    private var imagePageAdapter: ImagePageAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireHiddenMiniPlayer()
        updateState()

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

        // Long-pressing the position counter cycles the visualizer rendering mode
        // between the bar-spectrum and fluid water-wave styles.
        binding.count.setOnLongClickListener {
            val newMode = if (PlayerPreferences.getVisualizerMode() == PlayerPreferences.VISUALIZER_MODE_BARS) {
                PlayerPreferences.VISUALIZER_MODE_WAVE
            } else {
                PlayerPreferences.VISUALIZER_MODE_BARS
            }
            PlayerPreferences.setVisualizerMode(newMode)
            true
        }

        updateEqualizerButtonAlpha(PlayerPreferences.isVisualizerEnabled())

        // Collect real-time spectrum data and push it to the visualizer view.
        // repeatOnLifecycle(STARTED) automatically stops collection when the fragment
        // is paused/stopped (off-screen) and resumes when it becomes visible again.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                VisualizerManager.spectrumFlow.collect { bands ->
                    binding.visualizer.setBands(bands)
                }
            }
        }

        binding.visualizer.setCapColor(ThemeManager.accent.primaryAccentColor)
        binding.visualizer.particlesEnabled = true
    }

    private fun updateState() {
        val audio = MediaManager.getCurrentSong() ?: return
        binding.title.text = audio.title
        binding.artist.text = audio.artist
        binding.album.text = audio.album
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
        binding.title.setTypeWriting(audio.title ?: getString(R.string.unknown))
        binding.artist.setTypeWriting(audio.artist ?: getString(R.string.unknown))
        binding.album.setTypeWriting(audio.album ?: getString(R.string.unknown))
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