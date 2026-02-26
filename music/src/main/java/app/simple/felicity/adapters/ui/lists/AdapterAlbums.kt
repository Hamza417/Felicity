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
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import com.bumptech.glide.Glide

class AdapterAlbums(initial: List<Album>) : FastScrollAdapter<VerticalListViewHolder>() {

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

    private val diffCallback = object : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val albums: List<Album> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = albums[position].id

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
        val album = albums[position]
        when (holder) {
            is ListHolder -> holder.bind(album, isLightBind)
            is GridHolder -> holder.bind(album, isLightBind)
        }
    }

    override fun getItemViewType(position: Int): Int = AlbumPreferences.getGridType()
    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun getGeneralAdapterCallbacks(): GeneralAdapterCallbacks? = generalAdapterCallbacks

    fun updateList(newAlbums: List<Album>) {
        differ.submitList(newAlbums.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(album: Album, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(album.name)
            binding.tertiaryDetail.setTextOrUnknown(album.artist)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(album)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onAlbumLongClicked(albums, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onAlbumClicked(albums, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(album: Album, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(album.name)
            binding.tertiaryDetail.setTextOrUnknown(album.artist)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(album)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onAlbumLongClicked(albums, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onAlbumClicked(albums, bindingAdapterPosition, it)
            }
        }
    }
}
