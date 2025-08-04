package app.simple.felicity.ui.main.genres

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.adapters.ui.lists.genres.GenreDetailsAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.factories.genres.GenreViewerViewModelFactory
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.genres.GenreViewerViewModel

class GenrePage : MediaFragment() {

    private lateinit var binding: FragmentViewerGenresBinding
    private lateinit var genreViewerViewModel: GenreViewerViewModel

    private val genre: Genre by lazy {
        requireArguments().parcelable(BundleConstants.GENRE)
            ?: throw IllegalArgumentException("Genre is required")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentViewerGenresBinding.inflate(inflater, container, false)

        val factory = GenreViewerViewModelFactory(genre)
        genreViewerViewModel = ViewModelProvider(this, factory)[GenreViewerViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        genreViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${genre.name}, count: ${data.songs}")
            val adapter = GenreDetailsAdapter(data, genre)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter

            adapter.setGenreSongsAdapterListener(object : GenreDetailsAdapter.Companion.GenreSongsAdapterListener {
                override fun onSongClick(song: Song, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in genre: ${genre.name}, position: $position")
                    setMediaItems(listOf(song), position)
                }

                override fun onPlayClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
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