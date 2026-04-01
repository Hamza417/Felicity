package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.FavoritesPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

/**
 * Recycler adapter for the Favorites panel. Identical in structure to [AdapterSongs]
 * but reads [FavoritesPreferences.getGridSize] for its initial layout mode, keeping
 * the Favorites grid/list setting fully independent from the Songs panel.
 *
 * @author Hamza417
 */
class AdapterFavorites(initial: List<Audio>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Audio>() {
        override fun areItemsTheSame(oldItem: Audio, newItem: Audio) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.artist == newItem.artist &&
                    oldItem.album == newItem.album &&
                    oldItem.duration == newItem.duration &&
                    oldItem.path == newItem.path
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val songs: List<Audio> get() = differ.currentList

    /** Current layout mode; update this when the preference changes to trigger a view-type refresh. */
    var layoutMode: CommonPreferencesConstants.LayoutMode = FavoritesPreferences.getGridSize()

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = songs[position].id

    override fun getItemViewType(position: Int): Int =
        if (layoutMode.isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val song = songs[position]
        when (holder) {
            is ListHolder -> holder.bind(song, isLightBind)
            is GridHolder -> holder.bind(song, isLightBind)
        }
    }

    override fun getItemCount(): Int = songs.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    /** Registers the callback that handles click and long-click events on song items. */
    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /** Submits an updated list, triggering diff calculation on a background thread. */
    fun updateSongs(newSongs: List<Audio>) {
        differ.submitList(newSongs.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(audio: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.getArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            binding.container.setAudioID(audio.id)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(audio)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, binding.cover)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            binding.container.setAudioID(song.id)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(song)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, binding.albumArt)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }

    companion object {
        /** View type constant for a single-column list row. */
        const val VIEW_TYPE_LIST = 0

        /** View type constant for a multi-column grid cell. */
        const val VIEW_TYPE_GRID = 1
    }
}

