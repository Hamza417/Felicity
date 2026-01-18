package app.simple.felicity.ui.panels

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentCoverflowBinding
import app.simple.felicity.decorations.artflow.ArtFlowRenderer
import app.simple.felicity.dialogs.carousel.CarouselMenu.Companion.showCarouselMenu
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.popups.carousel.PopupSongsCarouselMenu
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.sort.SongSort.sorted
import app.simple.felicity.shared.utils.ConditionUtils.isNotZero
import app.simple.felicity.shared.utils.WindowUtil
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class ArtFlow : MediaFragment() {

    private lateinit var binding: FragmentCoverflowBinding
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCoverflowBinding.inflate(inflater, container, false)
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

        songsViewModel.getSongAndArt().observe(viewLifecycleOwner) { songs ->
            binding.coverflow.setUris(songs.keys.toList())
            binding.coverflow.scrollToIndex(songsViewModel.getCarouselPosition()).also {
                if (songsViewModel.getCarouselPosition().isNotZero()) {
                    binding.coverflow.reloadTextures()
                }
            }

            binding.coverflow.addScrollListener(object : ArtFlowRenderer.ScrollListener {
                override fun onCenteredIndexChanged(index: Int) {
                    songsViewModel.setCarouselPosition(index)
                }

                override fun onScrollOffsetChanged(offset: Float) {

                }

                override fun onSnapFinished(finalIndex: Int) {

                }

                override fun onSnapStarted(targetIndex: Int) {

                }
            })

            binding.coverflow.setOnCoverClickListener(object : app.simple.felicity.decorations.artflow.ArtFlow.OnCoverClickListener {
                override fun onCenteredCoverClick(index: Int, uri: Uri?) {
                    songsViewModel.setCarouselPosition(index)
                    val sorted = songs.values.toList().sorted()
                    Log.d(TAG, "onCenteredCoverClick: Playing ${sorted[index].title} at index $index")
                    setMediaItems(sorted, index)
                }

                override fun onSideCoverSelected(index: Int, uri: Uri?) {
                    songsViewModel.setCarouselPosition(index)
                }
            })

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
                PopupSongsCarouselMenu(
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
            openFragment(DefaultPlayer.Companion.newInstance(), DefaultPlayer.Companion.TAG)
        }
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        binding.title.text = song.title
        binding.artist.text = song.artist
        binding.art.loadArtCoverWithPayload(song)
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