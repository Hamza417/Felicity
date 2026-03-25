package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

class AdapterSearch(initial: List<Audio>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var attachedRecyclerView: RecyclerView? = null

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

    private val diffCallback = object : DiffUtil.ItemCallback<Audio>() {
        override fun areItemsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem.id == newItem.id
        }

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

    private val songs: List<Audio>
        get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = songs[position].id

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
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

    override fun getItemViewType(position: Int): Int = SearchPreferences.getGridType()

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    /**
     * Sets the general adapter callbacks used for song click and long-click handling.
     *
     * @param callbacks the [GeneralAdapterCallbacks] implementation to receive events.
     */
    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /**
     * Updates the songs list using [AsyncListDiffer] for efficient diffing.
     *
     * @param newSongs the updated list of [Audio] items to display.
     */
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
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }
}
