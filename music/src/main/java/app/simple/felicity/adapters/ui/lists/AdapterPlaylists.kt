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
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterPlaylistsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistWithSongs

/**
 * Recycler adapter for the Playlists panel.
 * Each item displays the playlist name and its current song count. The count is derived
 * directly from [PlaylistWithSongs.songs] which Room keeps reactive via the junction table,
 * so the displayed count automatically updates whenever songs are added or removed.
 *
 * @param initial The initial list of playlists with their song counts.
 *
 * @author Hamza417
 */
class AdapterPlaylists(initial: List<PlaylistWithSongs>) : RecyclerView.Adapter<AdapterPlaylists.ViewHolder>() {

    private var onPlaylistClicked: ((Playlist) -> Unit)? = null
    private var onPlaylistLongClicked: ((Playlist) -> Unit)? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 50) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 50) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) = notifyItemMoved(fromPosition, toPosition)

        override fun onChanged(position: Int, count: Int, payload: Any?) =
            notifyItemRangeChanged(position, count, payload)
    }

    private val diffCallback = object : DiffUtil.ItemCallback<PlaylistWithSongs>() {
        override fun areItemsTheSame(oldItem: PlaylistWithSongs, newItem: PlaylistWithSongs): Boolean =
            oldItem.playlist.id == newItem.playlist.id

        override fun areContentsTheSame(oldItem: PlaylistWithSongs, newItem: PlaylistWithSongs): Boolean =
            oldItem.playlist.name == newItem.playlist.name &&
                    oldItem.playlist.dateModified == newItem.playlist.dateModified &&
                    oldItem.playlist.isPinned == newItem.playlist.isPinned &&
                    oldItem.songs.size == newItem.songs.size
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val items: List<PlaylistWithSongs> get() = differ.currentList

    /**
     * Current layout mode. Update when the grid-size preference changes and call
     * [notifyItemRangeChanged] alongside updating the layout manager span count.
     */
    var layoutMode: CommonPreferencesConstants.LayoutMode = PlaylistPreferences.getGridSize()

    init {
        setHasStableIds(true)
        differ.submitList(initial)
    }

    override fun getItemId(position: Int): Long = items[position].playlist.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
                AdapterPlaylistsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    /**
     * Submits an updated playlist list. The differ runs the comparison on a background thread.
     *
     * @param list The new list of playlists with song counts.
     */
    fun updatePlaylists(list: List<PlaylistWithSongs>) {
        differ.submitList(list)
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

        fun bind(item: PlaylistWithSongs) {
            binding.name.text = item.playlist.name
            binding.count.text = binding.root.context.getString(R.string.x_songs, item.songs.size)

            binding.container.setOnClickListener {
                onPlaylistClicked?.invoke(item.playlist)
            }

            binding.container.setOnLongClickListener {
                onPlaylistLongClicked?.invoke(item.playlist)
                true
            }
        }
    }
}
