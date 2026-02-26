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
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown

class AdapterArtists(initial: List<Artist>) : FastScrollAdapter<VerticalListViewHolder>() {

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

    private val artists: List<Artist> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = artists[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
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
        val artist = artists[position]
        when (holder) {
            is ListHolder -> holder.bind(artist, isLightBind)
            is GridHolder -> holder.bind(artist, isLightBind)
        }
    }

    override fun getItemCount(): Int = artists.size
    override fun getItemViewType(position: Int): Int = ArtistPreferences.getGridType()

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun updateList(newArtists: List<Artist>) {
        differ.submitList(newArtists.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(artist: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_albums, artist.albumCount, artist.albumCount))
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(item = artist)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(artists, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(artists, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(artist: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(item = artist)

            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(artists, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(artists, bindingAdapterPosition, it)
            }
        }
    }
}