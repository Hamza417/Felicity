package app.simple.felicity.adapters.ui.lists.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.databinding.AdapterStylePeristyleBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import com.bumptech.glide.Glide

class AdapterAlbums(initial: List<Album>) :
        RecyclerView.Adapter<VerticalListViewHolder>(), SlideFastScroller.FastScrollBindingController {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private var albums = mutableListOf<Album>().apply { addAll(initial) }
    private var lightBindMode = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return albums[position].id
    }

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
            else -> {
                throw IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val album = albums[position]

        if (lightBindMode.not()) {
            when (holder) {
                is ListHolder -> {
                    holder.bind(album)
                }
                is GridHolder -> {
                    holder.bind(album)
                }
                is PeristyleHolder -> {
                    holder.bind(album)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return AlbumPreferences.getGridType()
    }

    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)

        when (holder) {
            is ListHolder -> {
                Glide.with(holder.binding.cover).clear(holder.binding.cover)
            }
            is GridHolder -> {
                Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            }
            is PeristyleHolder -> {
                Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            }
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    override fun setLightBindMode(enabled: Boolean) {
        lightBindMode = enabled
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, isLightBind: Boolean) {
        if (holder is VerticalListViewHolder) {
            if (isLightBind.not()) {
                val album = albums[position]
                when (holder) {
                    is ListHolder -> {
                        holder.bind(album)
                    }
                    is GridHolder -> {
                        holder.bind(album)
                    }
                    is PeristyleHolder -> {
                        holder.bind(album)
                    }
                }
            }
        }
    }

    override fun shouldHandleCustomBinding(): Boolean {
        return true
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }

        fun bind(album: Album) {
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

            binding.container.clearSkeletonBackground()
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }

        fun bind(album: Album) {
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

            binding.container.clearSkeletonBackground()
        }
    }

    inner class PeristyleHolder(val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }

        fun bind(album: Album) {
            binding.albumArt.loadPeristyleArtCover(album)
            binding.title.text = album.name
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onAlbumLongClicked(albums, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onAlbumClicked(albums, bindingAdapterPosition, it)
            }

            binding.container.clearSkeletonBackground()
        }
    }
}
