package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterGenresBinding
import app.simple.felicity.databinding.AdapterGenresListBinding
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre

class AdapterGenres(private val list: List<Genre>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_ITEM -> {
                ListHolder(
                        AdapterGenresListBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            RecyclerViewUtils.TYPE_ITEM_CARD -> {
                Holder(
                        AdapterGenresBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Holder -> holder.bind(list[position])
            is ListHolder -> holder.bind(list[position])
        }
    }

    override fun getItemId(position: Int): Long {
        return list[position].id
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (GenresPreferences.getGridSize() == CommonPreferencesConstants.GRID_SIZE_ONE) {
            RecyclerViewUtils.TYPE_ITEM
        } else {
            RecyclerViewUtils.TYPE_ITEM_CARD
        }
    }

    inner class Holder(private val binding: AdapterGenresBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.name.text = genre.name ?: context.getString(R.string.unknown)
            binding.cover.loadArtCoverWithPayload(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    inner class ListHolder(private val binding: AdapterGenresListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.name.text = genre.name ?: context.getString(R.string.unknown)
            binding.cover.loadArtCoverWithPayload(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }
}