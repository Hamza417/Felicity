package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterGenresBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.genres.GenreCoverModel
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.repository.models.Genre
import com.bumptech.glide.Glide

class AdapterGenres(private val list: List<Genre>) : androidx.recyclerview.widget.RecyclerView.Adapter<AdapterGenres.Holder>() {

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
            binding.name.text = genre.name
            binding.cover.createGenreCover(genre)

            // Set click listener if needed
            binding.root.setOnClickListener {
                // Handle click event for the genre item
            }
        }
    }

    companion object {
        fun ImageView.createGenreCover(genre: Genre) {
            Glide.with(context)
                .asBitmap()
                .dontTransform()
                .load(GenreCoverModel(context, genre.id, genreName = genre.name ?: context.getString(R.string.unknown)))
                .transform(Greyscale())
                .into(this)
        }
    }
}