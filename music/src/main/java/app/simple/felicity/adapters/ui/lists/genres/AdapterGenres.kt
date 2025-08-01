package app.simple.felicity.adapters.ui.lists.genres

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterGenresBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.genres.GenreCoverModel
import app.simple.felicity.repository.models.Genre
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class AdapterGenres(private val list: List<Genre>) : androidx.recyclerview.widget.RecyclerView.Adapter<AdapterGenres.Holder>() {

    private var genreClickListener: GenreClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
                AdapterGenresBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class Holder(private val binding: AdapterGenresBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.container.transitionName = genre.name ?: binding.root.context.getString(R.string.unknown)
            binding.name.text = genre.name
            Log.i(TAG, "bind: ${genre.name}")
            binding.cover.createGenreCover(genre)

            // Set click listener if needed
            binding.container.setOnClickListener {
                genreClickListener?.onGenreClicked(genre, it)
            }
        }
    }

    fun setGenreClickListener(listener: GenreClickListener) {
        this.genreClickListener = listener
    }

    companion object {
        fun ImageView.createGenreCover(genre: Genre, corner: Int = 48) {
            Glide.with(context)
                .asBitmap()
                .load(GenreCoverModel(context, genre.id, genreName = genre.name ?: context.getString(R.string.unknown)))
                .transform(CenterCrop())
                .into(this)
        }

        interface GenreClickListener {
            fun onGenreClicked(genre: Genre, view: View)
        }

        private const val TAG = "AdapterGenres"
    }
}