package app.simple.felicity.adapters.ui.lists.artists

import android.view.LayoutInflater
import android.view.ViewGroup
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
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown

class AdapterArtists(private val artists: List<Artist>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    init {
        setHasStableIds(true)
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
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                PeristyleHolder(AdapterStylePeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val artist = artists[position]
        when (holder) {
            is ListHolder -> holder.bind(artist, isLightBind)
            is GridHolder -> holder.bind(artist, isLightBind)
            is PeristyleHolder -> holder.bind(artist, isLightBind)
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

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(artist: Artist, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_albums, artist.albumCount, artist.albumCount))
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))
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
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))
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

    inner class PeristyleHolder(val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        fun bind(artist: Artist, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.albumArt.loadPeristyleArtCover(artist)
            binding.title.text = artist.name
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