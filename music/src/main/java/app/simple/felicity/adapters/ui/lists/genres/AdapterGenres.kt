package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.AdapterGenresBinding
import app.simple.felicity.databinding.AdapterHeaderGenresBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.repository.models.Genre

class AdapterGenres(private val list: List<Genre>) : androidx.recyclerview.widget.RecyclerView.Adapter<VerticalListViewHolder>() {

    private var genreClickListener: GenreClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(
                        AdapterHeaderGenresBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            else -> {
                Holder(
                        AdapterGenresBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Header) {
            // No binding needed for header
        } else if (holder is Holder) {
            val genre = list[position - 1] // Adjust for header
            holder.bind(genre)
        }
    }

    override fun getItemCount(): Int {
        return list.size.plus(1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    inner class Header(private val binding: AdapterHeaderGenresBinding) : VerticalListViewHolder(binding.root)

    inner class Holder(private val binding: AdapterGenresBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.name.text = genre.name
            binding.cover.loadGenreCover(genre)

            binding.container.setOnClickListener {
                genreClickListener?.onGenreClicked(genre, it)
            }
        }
    }

    fun setGenreClickListener(listener: GenreClickListener) {
        this.genreClickListener = listener
    }

    companion object {
        interface GenreClickListener {
            fun onGenreClicked(genre: Genre, view: View)
        }

        private const val TAG = "AdapterGenres"
    }
}