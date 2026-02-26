package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdapterSongs(initial: List<Audio>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1
    private var songs = mutableListOf<Audio>().apply { addAll(initial) }
    private var pendingDiffJob: Job? = null
    private var attachedRecyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    var currentlyPlayingSong: Audio? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { v -> songs.indexOfFirst { it.id == v.id } } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
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
            is ListHolder -> holder.bind(song, isLightBind)
            is GridHolder -> holder.bind(song, isLightBind)
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

    /**
     * Update the songs list with DiffUtil for efficient updates.
     * The diff is computed on a background thread, then dispatched on Main only after
     * any currently-running item animation has finished — so remove and insert animations
     * never collide and the adapter/RecyclerView state is always consistent.
     */
    fun updateSongs(newSongs: List<Audio>, scope: CoroutineScope) {
        pendingDiffJob?.cancel()
        val frozenOld = songs.toList()
        val frozenNew = newSongs.toList()
        pendingDiffJob = scope.launch(Dispatchers.Default) {
            val diffResult = DiffUtil.calculateDiff(SongsDiffCallback(frozenOld, frozenNew))
            withContext(Dispatchers.Main) {
                val rv = attachedRecyclerView
                val animator = rv?.itemAnimator
                if (animator != null && animator.isRunning) {
                    // Wait for the current animation to finish, then apply
                    animator.isRunning {
                        songs.clear()
                        songs.addAll(frozenNew)
                        diffResult.dispatchUpdatesTo(this@AdapterSongs)
                    }
                } else {
                    songs.clear()
                    songs.addAll(frozenNew)
                    diffResult.dispatchUpdatesTo(this@AdapterSongs)
                }
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class SongsDiffCallback(
            private val oldList: List<Audio>,
            private val newList: List<Audio>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldSong = oldList[oldItemPosition]
            val newSong = newList[newItemPosition]
            return oldSong.title == newSong.title &&
                    oldSong.artist == newSong.artist &&
                    oldSong.album == newSong.album &&
                    oldSong.duration == newSong.duration &&
                    oldSong.path == newSong.path
        }
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bindSelectionState(song: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(audio: Audio, isLightBind: Boolean) {
            // Always update text content so users see correct data during fast scroll
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            bindSelectionState(audio)

            if (isLightBind) {
                // Skip heavy operations: image loading
                return
            }

            // Full binding: load images
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
            // Always update text content so users see correct data during fast scroll
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            bindSelectionState(song)

            if (isLightBind) {
                // Skip heavy operations: image loading
                return
            }

            // Full binding: load images
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