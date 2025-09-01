package app.simple.felicity.adapters.ui.lists.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterAlbumsBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStylePeristyleBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.albumcover.AlbumCoverUtils.loadAlbumCover
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album

class AdapterAlbums(initial: List<Album>) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private var albums = mutableListOf<Album>().apply { addAll(initial) }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return albums[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterAlbumsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                PeristyleHolder(AdapterStylePeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                throw IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val album = albums[position]

        when (holder) {
            is ListHolder -> {
                holder.binding.title.setTextOrUnknown(album.name)
                holder.binding.artists.setTextOrUnknown(album.artist)
                holder.binding.count.setTextOrUnknown(holder.context.getString(R.string.x_songs, album.songCount))

                holder.binding.albumArt.loadAlbumCover(album)

                holder.binding.container.setOnLongClickListener {
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, position, it)
                    true
                }

                holder.binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onAlbumClicked(albums, holder.bindingAdapterPosition, it)
                }
            }
            is GridHolder -> {
                holder.binding.title.setTextOrUnknown(album.name)
                holder.binding.artists.setTextOrUnknown(album.artist)
                holder.binding.count.setTextOrUnknown(holder.context.getString(R.string.x_songs, album.songCount))

                holder.binding.albumArt.loadAlbumCover(album, skipCache = true)

                holder.binding.container.setOnLongClickListener {
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, position, it)
                    true
                }

                holder.binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onAlbumClicked(albums, holder.bindingAdapterPosition, it)
                }
            }
            is PeristyleHolder -> {
                holder.binding.albumArt.loadAlbumCover(album, crop = true, roundedCorners = false,
                                                       blurShadow = false, skipCache = false)
                holder.binding.title.text = album.name
                holder.binding.container.setOnLongClickListener {
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, position, it)
                    true
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return AlbumPreferences.getGridType()
    }

    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        // listHolder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        // Glide.with(listHolder.binding.albumArt).clear(listHolder.binding.albumArt)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    inner class ListHolder(val binding: AdapterAlbumsBinding) : VerticalListViewHolder(binding.root)

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root)

    inner class PeristyleHolder(val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root)
}
