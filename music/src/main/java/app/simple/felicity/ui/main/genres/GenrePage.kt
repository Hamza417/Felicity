package app.simple.felicity.ui.main.genres

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.songs.SongsAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.factories.genres.GenreViewerViewModelFactory
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Genre
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
        binding.container.transitionName = genre.toString()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.posterContainer.post {
            binding.recyclerView.post {
                binding.posterContainer.setPadding(
                        binding.posterContainer.paddingLeft,
                        binding.recyclerView.paddingTop + binding.posterContainer.paddingTop,
                        binding.posterContainer.paddingRight,
                        binding.posterContainer.paddingBottom
                )
                binding.recyclerView.setBackgroundColor(Color.TRANSPARENT)
                binding.recyclerView.let {
                    it.setPadding(
                            it.paddingLeft,
                            binding.posterContainer.height + it.paddingTop,
                            it.paddingRight,
                            it.paddingBottom
                    )
                }
            }
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val offset = recyclerView.computeVerticalScrollOffset()
                val fadeSpeed = 0.5f // 0.5 = twice as fast, 0.25 = four times as fast, etc.
                val maxOffset = (binding.posterContainer.height * fadeSpeed).coerceAtLeast(1f)
                val alpha = 1f - (offset / maxOffset).coerceIn(0f, 1f)
                binding.posterContainer.alpha = alpha
            }
        })

        binding.poster.loadGenreCover(genre)
        binding.name.text = genre.name ?: getString(R.string.unknown)

        genreViewerViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${genre.name}, count: ${songs.size}")
            val adapter = SongsAdapter(songs)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter
            binding.info.text = getString(R.string.songs_size, songs.size)

            adapter.onItemClickListener = { song, position, view ->
                Log.i(TAG, "Song clicked: ${song.title} by ${song.artist}")
                setMediaItems(songs, position)
            }

            binding.play.setOnClickListener {
                Log.i(TAG, "Play button clicked for genre: ${genre.name}")
                setMediaItems(songs, 0)
            }

            binding.shuffle.setOnClickListener {
                Log.i(TAG, "Shuffle button clicked for genre: ${genre.name}")
                setMediaItems(songs.shuffled(), 0)
            }
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