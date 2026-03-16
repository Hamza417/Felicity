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
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

class AdapterSongs(initial: List<Audio>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    // Backing field — always reflects the ID of the song the adapter last highlighted.
    // Write directly (no notifications) to sync without triggering extra redraws.
    // Use setCurrentSong() / currentlyPlayingSong setter for change-with-notify.
    private var trackedSongId: Long? = null

    /**
     * Notify the adapter that the playing song changed.
     * Clears the old highlight and sets the new one using surgical item updates.
     */
    var currentlyPlayingSong: Audio?
        get() = songs.firstOrNull { it.id == trackedSongId }
        set(value) {
            val newId = value?.id
            val oldId = trackedSongId
            trackedSongId = newId
            // Un-highlight the old item
            if (oldId != null && oldId != newId) {
                val oldIndex = songs.indexOfFirst { it.id == oldId }
                if (oldIndex != -1) notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            // Highlight the new item
            val newIndex = if (newId != null) songs.indexOfFirst { it.id == newId } else -1
            if (newIndex != -1) notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
        }

    /** Re-evaluates selection state for every item.
     *  Syncs the tracked ID first so the next song change knows the correct old item. */
    fun notifyCurrentSong() {
        trackedSongId = MediaManager.getCurrentSongId()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_PLAYBACK_STATE)
    }

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
        // Seed the tracked ID so the first song-change correctly un-highlights the
        // item that was highlighted by the initial full bind (which reads MediaManager directly).
        trackedSongId = MediaManager.getCurrentSongId()
        differ.submitList(initial.toList())
    }


    override fun getItemId(position: Int): Long = songs[position].id

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
        differ.submitList(newSongs.toList()) {
            // After the diff is applied, re-evaluate selection so newly visible items
            // reflecting the current song are highlighted correctly.
            notifyCurrentSong()
        }
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