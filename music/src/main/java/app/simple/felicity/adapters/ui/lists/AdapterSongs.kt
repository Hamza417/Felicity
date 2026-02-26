package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
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
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

class AdapterSongs(initial: List<Audio>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1
    private var attachedRecyclerView: RecyclerView? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeInserted(position, count)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
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

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    var currentlyPlayingSong: Audio? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { v -> songs.indexOfFirst { it.id == v.id } } ?: -1
            if (newIndex != -1) notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            if (oldIndex != -1 && oldIndex != newIndex) notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            previousIndex = newIndex
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
            is ListHolder -> {
                holder.bind(song, isLightBind)
            }
            is GridHolder -> {
                holder.bind(song, isLightBind)
            }
        }
    }

    override fun onBindPayload(holder: VerticalListViewHolder, position: Int, payloads: MutableList<Any>): Boolean {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val song = songs[position]
            when (holder) {
                is ListHolder -> holder.bindSelectionState(song)
                is GridHolder -> holder.bindSelectionState(song)
            }
            return true
        }
        return false
    }

    override fun getItemCount(): Int = songs.size
    override fun getItemViewType(position: Int): Int = SongsPreferences.getGridType()

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun updateSongs(newSongs: List<Audio>) {
        differ.submitList(newSongs.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bindSelectionState(song: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(audio: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            bindSelectionState(audio)
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
        fun bindSelectionState(song: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(song: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            bindSelectionState(song)
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
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}