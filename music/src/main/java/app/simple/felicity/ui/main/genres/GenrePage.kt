package app.simple.felicity.ui.main.genres

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.GenreDetailsAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.SongHolderSpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupGenreMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.main.artists.ArtistPage
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.genres.GenreViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

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

        genreViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${genre.name}, count: ${data.songs}")
            val adapter = GenreDetailsAdapter(data, genre)
            binding.recyclerView.addItemDecoration(SongHolderSpacingItemDecoration(
                    AppearancePreferences.DEFAULT_SPACING.toInt(),
                    AppearancePreferences.getListSpacing().toInt()))
            binding.recyclerView.adapter = adapter

            adapter.setGenreSongsAdapterListener(object : GenreDetailsAdapter.Companion.GenreSongsAdapterListener {
                override fun onSongClick(songs: List<Song>, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in genre: ${genre.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onPlayClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
                }

                override fun onArtistClicked(artist: Artist) {
                    Log.i(TAG, "onArtistClicked: Artist clicked in genre: ${genre.name}, artist: ${artist.name}")
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
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

                                }
                            },
                            onDismiss = {
                                Log.i(TAG, "onMenuClicked: Popup dismissed for genre: ${genre.name}")
                            }
                    ).show()

                }
            })
        }
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