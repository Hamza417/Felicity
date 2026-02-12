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
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupGenreMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.GenreViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GenrePage : MediaFragment() {

    private lateinit var binding: FragmentViewerGenresBinding

    private val genre: Genre by lazy {
        requireArguments().parcelable(BundleConstants.GENRE)
            ?: throw IllegalArgumentException("Genre is required")
    }

    private val genreViewerViewModel by viewModels<GenreViewerViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<GenreViewerViewModel.Factory> {
                    it.create(genre = genre)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentViewerGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: GenrePage for genre: ${genre.name}")

        // Observe StateFlow with proper lifecycle handling
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                genreViewerViewModel.data.collect { data ->
                    data?.let { updateGenrePage(it) }
                }
            }
        }
    }

    private fun updateGenrePage(data: PageData) {
        Log.d(TAG, "updateGenrePage: Updating UI for genre: ${genre.name} with ${data.songs.size} songs")
        val adapter = PageAdapter(data, PageAdapter.PageType.GenrePage(genre))
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecorationSafely(PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))
        binding.recyclerView.adapter = adapter

        adapter.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClick: Song clicked in genre: ${genre.name}, position: $position")
                setMediaItems(songs, position)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onPlayClick: Play button clicked for genre: ${genre.name}, position: $position")
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onShuffleClick: Shuffle button clicked for genre: ${genre.name}, position: $position")
                setMediaItems(audios.shuffled().toMutableList(), position)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked in genre: ${genre.name}")
                PopupGenreMenu(
                        container = requireActivity().findViewById(R.id.app_container),
                        anchorView = view,
                        menuItems = listOf(R.string.play, R.string.shuffle, R.string.add_to_queue, R.string.add_to_playlist),
                        menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_add_to_queue, R.drawable.ic_add_to_playlist),
                        onMenuItemClick = {
                            when (it) {
                                R.string.play -> {
                                    Log.i(TAG, "onMenuItemClick: Play clicked for genre: ${genre.name}")
                                    setMediaItems(data.songs.toMutableList(), 0)
                                }
                                R.string.shuffle -> {
                                    Log.i(TAG, "onMenuItemClick: Shuffle clicked for genre: ${genre.name}")
                                    setMediaItems(data.songs.shuffled().toMutableList(), 0)
                                }
                            }
                        },
                        onDismiss = {
                            Log.i(TAG, "onMenuClicked: Popup dismissed for genre: ${genre.name}")
                        }
                ).show()
            }
        })

        requireView().startTransitionOnPreDraw()
    }

    companion object {
        fun newInstance(genre: Genre): GenrePage {
            val args = Bundle()
            args.putParcelable(BundleConstants.GENRE, genre)
            val fragment = GenrePage()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "GenreSongs"
    }
}