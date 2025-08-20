package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.core.R
import app.simple.felicity.databinding.AdapterSongHeaderBinding
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class SongsAdapter(initial: List<Song>) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1

    private var songs = mutableListOf<Song>().apply { addAll(initial) }

    init {
        setHasStableIds(true)
    }

    var currentlyPlayingSong: Song? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { songs.indexOf(it) } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex + 1, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex + 1, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long {
        return if (position == 0) HEADER_ITEM_ID else songs[position - 1].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return if (viewType == RecyclerViewUtils.TYPE_HEADER) {
            val binding = AdapterSongHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            Header(binding)
        } else {
            val binding = AdapterSongsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            Holder(binding)
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            val song = songs[position - 1]
            holder.bind(song)

            holder.binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, holder.bindingAdapterPosition - 1, it)
            }

            holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE) && holder is Holder) {
            val song = songs[position - 1]
            holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = songs.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) RecyclerViewUtils.TYPE_HEADER else RecyclerViewUtils.TYPE_ITEM
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        if (holder is Holder) {
            holder.itemView.clearAnimation()
        }
        super.onViewRecycled(holder)
        if (holder is Holder) {
            Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /**
     * Update songs using DiffUtil while keeping header fixed at position 0.
     * All diff callback indices are offset by +1 when notifying this adapter,
     * so header never animates or shifts.
     */
    fun updateSongs(newSongs: List<Song>) {
        if (songs === newSongs) return // Same instance, skip
        val diffResult = DiffUtil.calculateDiff(SongsDiffCallback(songs, newSongs))
        songs = newSongs.toMutableList()
        diffResult.dispatchUpdatesTo(HeaderAwareListUpdateCallback(this))

        // Re-highlight playing song if present in new list
        currentlyPlayingSong?.let { cps ->
            val newIdx = songs.indexOf(cps)
            if (newIdx != -1) {
                notifyItemChanged(newIdx + 1, PAYLOAD_PLAYBACK_STATE)
            } else {
                // Playing song disappeared; clear previous highlight
                if (previousIndex != -1) notifyItemChanged(previousIndex + 1, PAYLOAD_PLAYBACK_STATE)
                previousIndex = -1
            }
        }
    }

    inner class Holder(val binding: AdapterSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                title.setTextOrUnknown(song.title)
                artists.setTextOrUnknown(song.artist)
                album.setTextOrUnknown(song.album)

                albumArt.loadSongCover(song)
                albumArt.transitionName = song.path
            }
        }
    }

    inner class Header(val binding: AdapterSongHeaderBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.menu.setOnClickListener { generalAdapterCallbacks?.onMenuClicked(binding.menu) }
            binding.filter.setOnClickListener { generalAdapterCallbacks?.onFilterClicked(binding.filter) }
            binding.sortStyle.setOnClickListener { generalAdapterCallbacks?.onSortClicked(binding.sortStyle) }
            updateSortStyle()
        }

        fun updateSortStyle() {
            binding.sortStyle.text = when (SongsPreferences.getSongSort()) {
                SongsPreferences.BY_TITLE -> binding.root.context.getString(R.string.title)
                SongsPreferences.BY_ARTIST -> binding.root.context.getString(R.string.artist)
                SongsPreferences.BY_ALBUM -> binding.root.context.getString(R.string.album)
                SongsPreferences.PATH -> binding.root.context.getString(R.string.path)
                SongsPreferences.BY_DATE_ADDED -> binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DATE_MODIFIED -> binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DURATION -> binding.root.context.getString(R.string.duration)
                SongsPreferences.BY_YEAR -> binding.root.context.getString(R.string.year)
                SongsPreferences.BY_TRACK_NUMBER -> binding.root.context.getString(R.string.track_number)
                SongsPreferences.BY_COMPOSER -> binding.root.context.getString(R.string.composer)
                else -> binding.root.context.getString(R.string.unknown)
            }
        }
    }

    private class SongsDiffCallback(
            private val old: List<Song>,
            private val new: List<Song>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].id == new[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }

    /** Offsets list diff updates by +1 to account for fixed header at position 0 */
    private class HeaderAwareListUpdateCallback(private val adapter: SongsAdapter) : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            adapter.notifyItemRangeInserted(position + 1, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            adapter.notifyItemRangeRemoved(position + 1, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            adapter.notifyItemMoved(fromPosition + 1, toPosition + 1)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            adapter.notifyItemRangeChanged(position + 1, count, payload)
        }
    }

    companion object {
        private const val HEADER_ITEM_ID = Long.MIN_VALUE // Unique stable ID for header
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}
