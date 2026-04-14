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
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleLabelsBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.AlbumArtistPreferences
import app.simple.felicity.repository.models.Artist

/**
 * Adapter for the Album Artists panel. Works exactly like [AdapterArtists] but respects
 * [AlbumArtistPreferences] for layout mode so the two panels can have independent display
 * settings. Nobody wants their album artists looking like their regular artists — or do they?
 *
 * @author Hamza417
 */
class AdapterAlbumArtists(initial: List<Artist>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

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

    private val diffCallback = object : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Artist, newItem: Artist) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val albumArtists: List<Artist> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = albumArtists[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_LABEL -> {
                LabelHolder(AdapterStyleLabelsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val albumArtist = albumArtists[position]
        when (holder) {
            is ListHolder -> holder.bind(albumArtist, isLightBind)
            is GridHolder -> holder.bind(albumArtist, isLightBind)
            is LabelHolder -> holder.bind(albumArtist, isLightBind)
        }
    }

    override fun getItemCount(): Int = albumArtists.size

    override fun getItemViewType(position: Int): Int {
        val mode = AlbumArtistPreferences.getGridSize()
        return when {
            mode.isLabel -> CommonPreferencesConstants.GRID_TYPE_LABEL
            mode.isGrid -> CommonPreferencesConstants.GRID_TYPE_GRID
            else -> CommonPreferencesConstants.GRID_TYPE_LIST
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
    }

    /** Wires up click and long-click events from the outside world. */
    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /** Replaces the current list smoothly using DiffUtil under the hood. */
    fun updateList(newAlbumArtists: List<Artist>) {
        differ.submitList(newAlbumArtists.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(albumArtist: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(albumArtist.name)
            binding.tertiaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_albums, albumArtist.albumCount, albumArtist.albumCount))
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, albumArtist.trackCount, albumArtist.trackCount))
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(item = albumArtist)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(albumArtists, bindingAdapterPosition, binding.cover)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(albumArtists, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(albumArtist: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(albumArtist.name)
            binding.tertiaryDetail.setTextOrUnknown(albumArtist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, albumArtist.trackCount, albumArtist.trackCount))
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(item = albumArtist)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(albumArtists, bindingAdapterPosition, binding.albumArt)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(albumArtists, bindingAdapterPosition, it)
            }
        }
    }

    inner class LabelHolder(val binding: AdapterStyleLabelsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(albumArtist: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(albumArtist.name)
            binding.tertiaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_albums, albumArtist.albumCount, albumArtist.albumCount))
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, albumArtist.trackCount, albumArtist.trackCount))
            if (isLightBind) return
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(albumArtists, bindingAdapterPosition, null)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(albumArtists, bindingAdapterPosition, it)
            }
        }
    }
}

