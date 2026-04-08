package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPlaylistsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.Playlist

/**
 * Recycler adapter for the Playlists panel.
 * Each item displays the playlist name and its total song count.
 * Tapping an item opens the playlist; long-pressing shows the context menu.
 *
 * @param initial The initial list of playlists to display.
 *
 * @author Hamza417
 */
class AdapterPlaylists(initial: List<Playlist>) : RecyclerView.Adapter<AdapterPlaylists.ViewHolder>() {

    private var onPlaylistClicked: ((Playlist) -> Unit)? = null
    private var onPlaylistLongClicked: ((Playlist) -> Unit)? = null

    /** Per-playlist song count map populated asynchronously. */
    private val songCountMap: MutableMap<Long, Int> = mutableMapOf()

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 50) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 50) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem.name == newItem.name &&
                    oldItem.dateModified == newItem.dateModified &&
                    oldItem.isPinned == newItem.isPinned
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val playlists: List<Playlist> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial)
    }

    override fun getItemId(position: Int): Long = playlists[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
                AdapterPlaylistsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = playlists.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    /**
     * Submits an updated playlist list. The differ runs the comparison on a background thread.
     *
     * @param list The new list of playlists.
     */
    fun updatePlaylists(list: List<Playlist>) {
        differ.submitList(list)
    }

    /**
     * Updates the displayed song count for a single playlist row.
     *
     * @param playlistId The playlist whose count changed.
     * @param count      The new song count.
     */
    fun updateSongCount(playlistId: Long, count: Int) {
        songCountMap[playlistId] = count
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) notifyItemChanged(index)
    }

    /** Registers a callback fired when the user taps a playlist row. */
    fun setOnPlaylistClicked(block: (Playlist) -> Unit) {
        onPlaylistClicked = block
    }

    /** Registers a callback fired when the user long-presses a playlist row. */
    fun setOnPlaylistLongClicked(block: (Playlist) -> Unit) {
        onPlaylistLongClicked = block
    }

    inner class ViewHolder(private val binding: AdapterPlaylistsBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.name.text = playlist.name
            val count = songCountMap[playlist.id] ?: 0
            binding.count.text = binding.root.context.getString(R.string.x_songs, count)

            binding.container.setOnClickListener {
                onPlaylistClicked?.invoke(playlist)
            }

            binding.container.setOnLongClickListener {
                onPlaylistLongClicked?.invoke(playlist)
                true
            }
        }
    }
}

