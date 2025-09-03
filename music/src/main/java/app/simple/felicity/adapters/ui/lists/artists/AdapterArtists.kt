package app.simple.felicity.adapters.ui.lists.artists

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
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist

class AdapterArtists(private val artists: List<Artist>) :
        RecyclerView.Adapter<VerticalListViewHolder>(), SlideFastScroller.FastScrollBindingController {
    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var lightBindMode = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return artists[position].id
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
        val artist = artists[position]
        if (lightBindMode.not()) {
            when (holder) {
                is ListHolder -> {
                    holder.bind(artist)
                }
                is GridHolder -> {
                    holder.bind(artist)
                }
                is PeristyleHolder -> {
                    holder.bind(artist)
                }
            }
        }
    }

    override fun getItemCount(): Int = artists.size

    override fun getItemViewType(position: Int): Int {
        return ArtistPreferences.getGridType()
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)

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
                val album = artists[position]
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

        fun bind(artist: Artist) {
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))

            binding.cover.loadArtCover(artist)

            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(artists, bindingAdapterPosition, it)
                true
            }

            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(artists, bindingAdapterPosition, it)
            }

            binding.container.clearSkeletonBackground()
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }

        fun bind(artist: Artist) {
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))

            binding.albumArt.loadArtCover(artist, skipCache = true)

            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(artists, bindingAdapterPosition, it)
                true
            }

            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(artists, bindingAdapterPosition, it)
            }

            binding.container.clearSkeletonBackground()
        }
    }

    inner class PeristyleHolder(val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }

        fun bind(artist: Artist) {
            binding.albumArt.loadPeristyleArtCover(artist)
            binding.title.text = artist.name
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onArtistLongClicked(artists, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onArtistClicked(artists, bindingAdapterPosition, it)
            }

            binding.container.clearSkeletonBackground()
        }
    }

}