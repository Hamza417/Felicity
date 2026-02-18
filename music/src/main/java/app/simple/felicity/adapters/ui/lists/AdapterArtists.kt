package app.simple.felicity.adapters.ui.lists

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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

class AdapterArtists(artists: List<Artist>) : FastScrollAdapter<VerticalListViewHolder>() {

    private val artists: MutableList<Artist> = ArrayList(artists)
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
            else -> throw IllegalArgumentException("Invalid view type")
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

    /**
     * Update the list of artists with DiffUtil for efficient updates
     * This is called when the Flow emits new data from the database
     */
    fun updateList(newArtists: List<Artist>) {
        Log.d(TAG, "updateList: Old size=${artists.size}, New size=${newArtists.size}")
        if (newArtists.isNotEmpty()) {
            Log.d(TAG, "First new artist: id=${newArtists.first().id}, name=${newArtists.first().name}, trackCount=${newArtists.first().trackCount}")
        }

        val diffCallback = ArtistsDiffCallback(artists, newArtists)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        artists.clear()
        artists.addAll(newArtists)

        Log.d(TAG, "After update: artists.size=${artists.size}")
        diffResult.dispatchUpdatesTo(this)
        Log.d(TAG, "DiffUtil updates dispatched")
    }

    companion object {
        private const val TAG = "AdapterArtists"
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class ArtistsDiffCallback(
            private val oldList: List<Artist>,
            private val newList: List<Artist>
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
        fun bind(artist: Artist, isLightBind: Boolean) {
            // Always update text content so users see correct data during fast scroll
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_albums, artist.albumCount, artist.albumCount))
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))

            if (isLightBind) {
                // Skip heavy operations: image loading
                return
            }

            // Full binding: load images
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
            // Always update text content so users see correct data during fast scroll
            binding.title.setTextOrUnknown(artist.name)
            binding.tertiaryDetail.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount))

            if (isLightBind) {
                // Skip heavy operations: image loading
                return
            }

            // Full binding: load images
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