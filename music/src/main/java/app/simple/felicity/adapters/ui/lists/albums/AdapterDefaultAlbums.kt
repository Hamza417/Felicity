package app.simple.felicity.adapters.ui.lists.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterAlbumsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.albumcover.AlbumCoverUtils.loadAlbumCover
import app.simple.felicity.repository.models.Album
import com.bumptech.glide.Glide
import java.util.ArrayDeque

class AdapterDefaultAlbums(initial: List<Album>, preInflate: Int = 0) :
        RecyclerView.Adapter<AdapterDefaultAlbums.Holder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1

    private var albums = mutableListOf<Album>().apply { addAll(initial) }

    // Optional async pre-inflation pool
    private val preInflationPool: SongItemPreInflationPool? =
        if (preInflate > 0) SongItemPreInflationPool(preInflate) else null

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // Start async pre-inflation once we have the parent (needed for proper LayoutParams)
        preInflationPool?.start(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        preInflationPool?.clear()
    }

    var currentlyPlayingSong: Album? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { albums.indexOf(it) } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long {
        return albums[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // Try to acquire a pre-inflated view; fallback to normal synchronous inflate
        return Holder(AdapterAlbumsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = albums[position]
        holder.bind(song)

        holder.binding.container.setOnClickListener {
            generalAdapterCallbacks?.onAlbumClicked(albums, holder.bindingAdapterPosition, it)
        }

        holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val song = albums[position]
            holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /**
     * Update songs using DiffUtil while keeping header fixed at position 0.
     * All diff callback indices are offset by +1 when notifying this adapter,
     * so header never animates or shifts.
     */
    fun updateSongs(newSongs: List<Album>) {
        if (albums === newSongs) return // Same instance, skip
        val diffResult = DiffUtil.calculateDiff(SongsDiffCallback(albums, newSongs))
        albums = newSongs.toMutableList()
        diffResult.dispatchUpdatesTo(HeaderAwareListUpdateCallback(this))

        // Re-highlight playing song if present in new list
        currentlyPlayingSong?.let { cps ->
            val newIdx = albums.indexOf(cps)
            if (newIdx != -1) {
                notifyItemChanged(newIdx, PAYLOAD_PLAYBACK_STATE)
            } else {
                // Playing song disappeared; clear previous highlight
                if (previousIndex != -1) notifyItemChanged(previousIndex, PAYLOAD_PLAYBACK_STATE)
                previousIndex = -1
            }
        }
    }

    inner class Holder(val binding: AdapterAlbumsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(album: Album) {
            binding.apply {
                title.setTextOrUnknown(album.name)
                artists.setTextOrUnknown(album.artist)
                count.setTextOrUnknown(context.getString(R.string.x_songs, album.songCount))

                albumArt.loadAlbumCover(album)

                container.setOnLongClickListener {
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, bindingAdapterPosition, it)
                    true
                }
            }
        }
    }

    private class SongsDiffCallback(
            private val old: List<Album>,
            private val new: List<Album>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].id == new[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }

    /** Offsets list diff updates by +1 to account for fixed header at position 0 */
    private class HeaderAwareListUpdateCallback(private val adapter: AdapterDefaultAlbums) : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            adapter.notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            adapter.notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            adapter.notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            adapter.notifyItemRangeChanged(position, count, payload)
        }
    }

    companion object {
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}

/**
 * Simple async pre-inflation pool for adapter_songs layout.
 * - Inflates up to [capacity] item views off the main thread (XML parsing part) using AsyncLayoutInflater.
 * - Views are acquired in onCreateViewHolder; falls back to synchronous inflate if pool empty or not ready.
 * NOTE: Complex view hierarchies or binding logic still run on main thread when binding data.
 */
private class SongItemPreInflationPool(private val capacity: Int) {
    private val queue = ArrayDeque<View>()

    @Volatile
    private var started = false

    fun start(parent: ViewGroup) {
        if (started) return
        started = true
        repeat(capacity) {
            AsyncLayoutInflater(parent.context).inflate(R.layout.adapter_songs, parent
            ) { view: View, _: Int, _: ViewGroup? ->
                synchronized(queue) { queue.addLast(view) }
            }
        }
    }

    fun acquire(@Suppress("UNUSED_PARAMETER") parent: ViewGroup): View? {
        synchronized(queue) { return if (queue.isEmpty()) null else queue.removeFirst() }
    }

    fun clear() {
        synchronized(queue) { queue.clear() }
    }
}
