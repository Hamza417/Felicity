package app.simple.felicity.ui.pages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.ArtistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ArtistPage : MediaFragment() {

    private lateinit var binding: FragmentPageArtistBinding
    private var pageAdapter: PageAdapter? = null

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

        Log.d(TAG, "onViewCreated: ArtistPage for artist: ${artist.name}, adapter=${pageAdapter != null}")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                artistViewerViewModel.data.collect { data ->
                    data?.let { updateArtistPage(it) }
                }
            }
        }
    }

    private fun updateArtistPage(data: PageData) {
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecorationSafely(PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))


        if (pageAdapter == null) {
            Log.d(TAG, "updateArtistPage: Creating new adapter")
            pageAdapter = PageAdapter(data, PageAdapter.PageType.ArtistPage(artist))
            binding.recyclerView.adapter = pageAdapter
            setupAdapterCallbacks()
        } else {
            Log.d(TAG, "updateArtistPage: Updating existing adapter with new data")
            pageAdapter?.updateData(data)

            // Re-attach adapter if RecyclerView lost its reference (e.g., after navigation)
            if (binding.recyclerView.adapter == null) {
                Log.d(TAG, "updateArtistPage: Re-attaching adapter to RecyclerView")
                binding.recyclerView.adapter = pageAdapter
            }
        }

        requireView().startTransitionOnPreDraw()
    }

    private fun setupAdapterCallbacks() {
        pageAdapter?.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClick: Song clicked in artist: ${artist.name}, position: $position")
                setMediaItems(songs, position)
            }

            override fun onSongLongClicked(songs: List<Audio>, position: Int, view: View) {
                openSongsMenu(songs, position, view as ImageView)
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

            override fun onGenreClicked(genre: Genre, view: View) {
                super.onGenreClicked(genre, view)
                Log.i(TAG, "onGenreClicked: Genre clicked: ${genre.name}")
                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked for artist: ${artist.name}")

                // Get current data from adapter
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentData = artistViewerViewModel.data.value ?: return@launch

                    PopupArtistMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> {
                                        Log.i(TAG, "onMenuItemClick: Play clicked for artist: ${artist.name}")
                                        setMediaItems(currentData.songs.toMutableList(), 0)
                                    }
                                    R.string.shuffle -> {
                                        Log.i(TAG, "onMenuItemClick: Shuffle clicked for artist: ${artist.name}")
                                        setMediaItems(currentData.songs.shuffled().toMutableList(), 0)
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
            }
        })
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