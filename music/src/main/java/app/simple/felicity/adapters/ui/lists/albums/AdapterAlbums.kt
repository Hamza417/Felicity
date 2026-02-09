package app.simple.felicity.adapters.ui.lists.albums

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.databinding.AdapterStylePeristyleBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import com.bumptech.glide.Glide

class AdapterAlbums(initial: List<Album>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var albums = mutableListOf<Album>().apply {
        addAll(initial)
        Log.d(TAG, "AdapterAlbums: Initialized with ${initial.size} albums")
        if (initial.isNotEmpty()) {
            Log.d(TAG, "First album: id=${initial.first().id}, name=${initial.first().name}, songCount=${initial.first().songCount}")
        }
    }

    init {
        setHasStableIds(true)
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
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                PeristyleHolder(AdapterStylePeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        if (position >= albums.size) {
            Log.e(TAG, "onBind: Invalid position $position, albums.size=${albums.size}")
            return
        }

        val album = albums[position]
        Log.d(TAG, "onBind: position=$position, album=${album.name}, id=${album.id}")

        when (holder) {
            is ListHolder -> holder.bind(album, isLightBind)
            is GridHolder -> holder.bind(album, isLightBind)
            is PeristyleHolder -> holder.bind(album, isLightBind)
        }
    }

    override fun getItemViewType(position: Int): Int = AlbumPreferences.getGridType()

    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            is PeristyleHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun getGeneralAdapterCallbacks(): GeneralAdapterCallbacks? {
        return generalAdapterCallbacks
    }

    /**
     * Update the adapter's list with new data using DiffUtil for efficiency.
     * This is called when the Flow emits new data from the database
     */
    fun updateList(newAlbums: List<Album>) {
        Log.d(TAG, "updateList: Old size=${albums.size}, New size=${newAlbums.size}")
        if (newAlbums.isNotEmpty()) {
            Log.d(TAG, "First new album: id=${newAlbums.first().id}, name=${newAlbums.first().name}, songCount=${newAlbums.first().songCount}")
        }

        val diffCallback = AlbumsDiffCallback(albums, newAlbums)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        albums.clear()
        albums.addAll(newAlbums)

        Log.d(TAG, "After update: albums.size=${albums.size}")
        diffResult.dispatchUpdatesTo(this)
        Log.d(TAG, "DiffUtil updates dispatched")
    }

    companion object {
        private const val TAG = "AdapterAlbums"
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class AlbumsDiffCallback(
            private val oldList: List<Album>,
            private val newList: List<Album>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(album: Album, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(album.name)
            binding.tertiaryDetail.setTextOrUnknown(album.artist)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))
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
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(album.name)
            binding.tertiaryDetail.setTextOrUnknown(album.artist)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))
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

    inner class PeristyleHolder(val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        fun bind(album: Album, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.albumArt.loadPeristyleArtCover(album)
            binding.title.text = album.name
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
