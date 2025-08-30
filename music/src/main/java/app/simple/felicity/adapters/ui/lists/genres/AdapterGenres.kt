package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterGenresBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.repository.models.Genre

class AdapterGenres(private val list: List<Genre>) : androidx.recyclerview.widget.RecyclerView.Adapter<AdapterGenres.Holder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
                AdapterGenresBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemId(position: Int): Long {
        return list[position].id
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class Holder(private val binding: AdapterGenresBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.name.text = genre.name
            binding.cover.loadGenreCover(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }
}