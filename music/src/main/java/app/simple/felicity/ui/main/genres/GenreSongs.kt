package app.simple.felicity.ui.main.genres

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.songs.SongsAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.factories.genres.GenreViewerViewModelFactory
import app.simple.felicity.glide.genres.GenreCoverModel
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.genres.GenreViewerViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class GenreSongs : MediaFragment() {

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
        postponeEnterTransition()

        binding.posterContainer.post {
            binding.recyclerView.post {
                binding.recyclerView.setBackgroundColor(Color.TRANSPARENT)
                binding.recyclerView.let {
                    it.setPadding(
                            it.paddingLeft,
                            binding.posterContainer.height,
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

        binding.poster.createGenreCover(genre)
        binding.name.text = genre.name ?: getString(R.string.unknown)

        genreViewerViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${genre.name}, count: ${songs.size}")
            val adapter = SongsAdapter(songs)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter

            adapter.onItemClickListener = { song, position, view ->
                // Handle song click
                Log.i(TAG, "Song clicked: ${song.title} by ${song.artist}")
                setMediaItems(songs, position)
            }

            view.startTransitionOnPreDraw()
        }
    }

    fun ImageView.createGenreCover(genre: Genre) {
        Glide.with(context)
            .asBitmap()
            .load(GenreCoverModel(context, genre.id, genreName = genre.name ?: context.getString(R.string.unknown)))
            .transform(CenterCrop())
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(this)
    }

    companion object {
        fun newInstance(genre: Genre): GenreSongs {
            val args = Bundle()
            args.putParcelable(BundleConstants.GENRE, genre)
            val fragment = GenreSongs()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "GenreSongs"
    }
}