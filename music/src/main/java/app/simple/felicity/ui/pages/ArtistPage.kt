package app.simple.felicity.ui.pages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.ArtistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ArtistPage : MediaFragment() {

    private lateinit var binding: FragmentPageArtistBinding

    private val artistViewerViewModel: ArtistViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<ArtistViewerViewModel.Factory>() {
                    it.create(artist = artist)
                }
            }
    )

    private val artist: Artist by lazy {
        requireArguments().parcelable(BundleConstants.ARTIST)
            ?: throw IllegalArgumentException("Artist is required")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: ArtistPage for artist: ${artist.name}")

        // Observe StateFlow with proper lifecycle handling
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                artistViewerViewModel.data.collect { data ->
                    data?.let { updateArtistPage(it) }
                }
            }
        }
    }

    private fun updateArtistPage(data: PageData) {
        Log.d(TAG, "updateArtistPage: Updating UI for artist: ${artist.name} with ${data.songs.size} songs")
        val adapter = PageAdapter(data, PageAdapter.PageType.ArtistPage(artist))
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecoration(PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))
        binding.recyclerView.adapter = adapter

        adapter.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClick: Song clicked in artist: ${artist.name}, position: $position")
                setMediaItems(songs, position)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onPlayClick: Play button clicked for artist: ${artist.name}, position: $position")
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onShuffleClick: Shuffle button clicked for artist: ${artist.name}, position: $position")
                setMediaItems(audios.shuffled().toMutableList(), position)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(newInstance(artists[position]), TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                val album = albums[position]
                Log.i(TAG, "onAlbumClicked: Album clicked: ${album.name}")
                openFragment(AlbumPage.newInstance(album), AlbumPage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked for artist: ${artist.name}")

                PopupArtistMenu(
                        container = requireContainerView(),
                        anchorView = view,
                        menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                        onMenuItemClick = {
                            when (it) {
                                R.string.play -> {
                                    Log.i(TAG, "onMenuItemClick: Play clicked for artist: ${artist.name}")
                                    setMediaItems(data.songs.toMutableList(), 0)
                                }
                                R.string.shuffle -> {
                                    Log.i(TAG, "onMenuItemClick: Shuffle clicked for artist: ${artist.name}")
                                    setMediaItems(data.songs.shuffled().toMutableList(), 0)
                                }
                                R.string.send -> {
                                    Log.i(TAG, "onMenuItemClick: Send clicked for artist: ${artist.name}")
                                    // TODO: Implement send functionality
                                }
                            }
                        },
                        menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_send),
                        onDismiss = { Log.d(TAG, "PopupArtistMenu dismissed") }
                ).show()
            }
        })

        requireView().startTransitionOnPreDraw()
    }

    companion object {
        const val TAG = "ArtistPage"

        fun newInstance(artist: Artist): ArtistPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.ARTIST, artist)
            val fragment = ArtistPage()
            fragment.arguments = args
            return fragment
        }
    }
}