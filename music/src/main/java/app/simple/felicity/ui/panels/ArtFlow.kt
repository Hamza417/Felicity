package app.simple.felicity.ui.panels

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentArtflowBinding
import app.simple.felicity.decorations.artflow.ArtFlow.OnCoverClickListener
import app.simple.felicity.decorations.artflow.ArtFlowDataProvider
import app.simple.felicity.decorations.artflow.ArtFlowRenderer
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.carousel.CarouselMenu.Companion.showCarouselMenu
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ConditionUtils.isNotZero
import app.simple.felicity.shared.utils.WindowUtil
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.panels.SongsViewModel
import kotlinx.coroutines.launch

class ArtFlow : MediaFragment() {

    private lateinit var binding: FragmentArtflowBinding
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })
    private val coverCache = ArtFlowCoverCache(maxMemoryCacheSizeMB = 50)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentArtflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireLightBarIcons()
        requireHiddenMiniPlayer()

        WindowUtil.getStatusBarHeightWhenAvailable(binding.topMenuContainer) { height ->
            binding.topMenuContainer.setPadding(
                    binding.topMenuContainer.paddingLeft,
                    height,
                    binding.topMenuContainer.paddingRight,
                    binding.topMenuContainer.paddingBottom
            )
        }

        // Observe StateFlow with proper lifecycle handling for immediate updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                songsViewModel.songs.collect { audioList ->
                    updateCarousel(audioList)
                }
            }
        }

        // Set up scroll listeners once (outside the collect block)
        binding.coverflow.addScrollListener(object : ArtFlowRenderer.ScrollListener {
            override fun onCenteredIndexChanged(index: Int) {
                songsViewModel.setCarouselPosition(index)
                // Preload covers around the new position - reduced radius and size for memory efficiency
                coverCache.preloadAround(index, radius = 8, maxDimension = 512)
            }

            override fun onScrollOffsetChanged(offset: Float) {
                // No-op
            }

            override fun onSnapFinished(finalIndex: Int) {
                // No-op
            }

            override fun onSnapStarted(targetIndex: Int) {
                // No-op
            }
        })

        // Set up cover click listener once
        binding.coverflow.setOnCoverClickListener(object : OnCoverClickListener {
            override fun onCenteredCoverClick(index: Int, itemId: Any?) {
                songsViewModel.setCarouselPosition(index)
                // Uncomment when ready to enable playback from carousel
                // viewLifecycleOwner.lifecycleScope.launch {
                //     songsViewModel.songs.value.let { songs ->
                //         if (index in songs.indices) {
                //             setMediaItems(songs, index)
                //         }
                //     }
                // }
            }

            override fun onSideCoverSelected(index: Int, itemId: Any?) {
                songsViewModel.setCarouselPosition(index)
            }
        })

        // Set up all click listeners once
        binding.arrowLeft.setOnClickListener {
            binding.coverflow.scrollToIndex(binding.coverflow.getCenteredIndex() - 10)
        }

        binding.arrowRight.setOnClickListener {
            binding.coverflow.scrollToIndex(binding.coverflow.getCenteredIndex() + 10)
        }

        binding.filter.setOnClickListener {
            childFragmentManager.showSongsSort()
        }

        binding.menu.setOnClickListener {
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = it,
                    menuItems = listOf(R.string.carousel_settings, R.string.songs_settings),
                    menuIcons = listOf(R.drawable.ic_carousel,
                                       R.drawable.ic_song_16dp),
                    onMenuItemClick = { id ->
                        when (id) {
                            R.string.songs_settings -> {
                                parentFragmentManager.showSongsMenu()
                            }
                            R.string.carousel_settings -> {
                                childFragmentManager.showCarouselMenu()
                            }
                        }
                    },
                    onDismiss = {}
            ).show()
        }

        binding.play.setOnLongClickListener {
            val currentSong = MediaManager.getCurrentSong()
            viewLifecycleOwner.lifecycleScope.launch {
                songsViewModel.songs.value.forEachIndexed { index, audio ->
                    if (audio.id == currentSong?.id) {
                        binding.coverflow.scrollToIndex(index)
                        return@forEachIndexed
                    }
                }
            }
            true
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
        }

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.miniplayerContainer.setOnClickListener {
            openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coverCache.release()
    }

    /**
     * Updates the carousel with a new list of audio items
     */
    private fun updateCarousel(audioList: List<Audio>) {
        // Update cache with new audio list
        coverCache.setAudioList(audioList)

        val provider = AlbumArtProvider(audioList)
        binding.coverflow.setDataProvider(provider)
        binding.coverflow.scrollToIndex(songsViewModel.getCarouselPosition()).also {
            if (songsViewModel.getCarouselPosition().isNotZero()) {
                binding.coverflow.reloadTextures()
            }
        }

        // Start preloading covers around the current position - reduced for memory efficiency
        coverCache.preloadAround(songsViewModel.getCarouselPosition(), radius = 8, maxDimension = 512)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        binding.title.text = audio.title
        binding.artist.text = audio.artist
        binding.art.loadArtCoverWithPayload(audio)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> {
                binding.play.setImageResource(app.simple.felicity.decoration.R.drawable.ic_pause)
            }
            MediaConstants.PLAYBACK_PAUSED -> {
                binding.play.setImageResource(app.simple.felicity.decoration.R.drawable.ic_play)
            }
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    inner class AlbumArtProvider(private val audioList: List<Audio>) : ArtFlowDataProvider {
        override fun getItemCount(): Int {
            return audioList.size
        }

        override fun loadArtwork(index: Int, maxDimension: Int): Bitmap? {
            // Try to get from cache first (non-blocking)
            coverCache.getOrNull(index)?.let { return it }

            // If not in cache, load synchronously as fallback
            // Use the reduced maxDimension for memory efficiency
            return coverCache.loadSync(index, maxDimension.coerceAtMost(512))
        }
    }

    companion object {
        fun newInstance(): ArtFlow {
            val args = Bundle()
            val fragment = ArtFlow()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "ArtFlow"
    }
}