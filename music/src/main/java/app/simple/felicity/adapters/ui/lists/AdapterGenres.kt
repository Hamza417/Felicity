package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterGenresListBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterGenres(initial: List<Genre>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeInserted(position, count)
            }
        }

        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeRemoved(position, count)
            }
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Genre>() {
        override fun areItemsTheSame(oldItem: Genre, newItem: Genre) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Genre, newItem: Genre) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val list: List<Genre> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterGenresListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                throw IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val genre = list[position]
        when (holder) {
            is GridHolder -> holder.bind(genre, isLightBind)
            is ListHolder -> holder.bind(genre, isLightBind)
        }
    }

    override fun getItemId(position: Int): Long = list[position].id
    override fun getItemCount(): Int = list.size
    override fun getItemViewType(position: Int): Int = GenresPreferences.getGridType()

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.tertiaryDetail.gone(false)
            binding.secondaryDetail.gone(false)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(genre)
            binding.container.setOnClickListener { callbacks?.onGenreClicked(genre, it) }
        }
    }

    inner class ListHolder(private val binding: AdapterGenresListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            binding.name.text = genre.name ?: context.getString(R.string.unknown)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(genre)
            binding.container.setOnClickListener { callbacks?.onGenreClicked(genre, it) }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    fun updateList(newGenres: List<Genre>) {
        differ.submitList(newGenres.toList())
    }
}