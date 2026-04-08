package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.R
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.StringUtils.ifNullOrBlank
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleLabelsBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistWithSongs
import com.bumptech.glide.Glide

/**
 * Recycler adapter for the Playlists panel.
 *
 * Supports all three [CommonPreferencesConstants.LayoutMode] variants (List, Grid, Label)
 * by reusing the shared [AdapterStyleListBinding], [AdapterStyleGridBinding], and
 * [AdapterStyleLabelsBinding] layouts. The three text fields are mapped as follows:
 * - title          ← playlist name
 * - secondaryDetail ← formatted song count (e.g., "5 Songs")
 * - tertiaryDetail  ← playlist description (empty when none is set)
 *
 * The count is derived directly from [PlaylistWithSongs.songs], which Room keeps
 * reactive via the junction table, so it updates automatically when songs are
 * added or removed.
 *
 * @param initial The initial list of playlists with their associated songs.
 *
 * @author Hamza417
 */
class AdapterPlaylists(initial: List<PlaylistWithSongs>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var onPlaylistClicked: ((Playlist) -> Unit)? = null
    private var onPlaylistLongClicked: ((PlaylistWithSongs, ImageView?) -> Unit)? = null

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
                    oldItem.playlist.description == newItem.playlist.description &&
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
     * Current layout mode. Update this when the grid-size preference changes and call
     * [notifyItemRangeChanged] alongside updating the layout manager span count.
     */
    var layoutMode: CommonPreferencesConstants.LayoutMode = PlaylistPreferences.getGridSize()

    init {
        setHasStableIds(true)
        differ.submitList(initial)
    }

    override fun getItemId(position: Int): Long = items[position].playlist.id

    override fun getItemViewType(position: Int): Int {
        return when {
            layoutMode.isLabel -> CommonPreferencesConstants.GRID_TYPE_LABEL
            layoutMode.isGrid -> CommonPreferencesConstants.GRID_TYPE_GRID
            else -> CommonPreferencesConstants.GRID_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_GRID ->
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            CommonPreferencesConstants.GRID_TYPE_LABEL ->
                LabelHolder(AdapterStyleLabelsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else ->
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val item = items[position]
        when (holder) {
            is ListHolder -> holder.bind(item, isLightBind)
            is GridHolder -> holder.bind(item, isLightBind)
            is LabelHolder -> holder.bind(item, isLightBind)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            is LabelHolder -> Unit
        }
    }

    /**
     * Submits an updated playlist list to the differ. The comparison runs on a background thread.
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
    fun setOnPlaylistLongClicked(block: (PlaylistWithSongs, ImageView?) -> Unit) {
        onPlaylistLongClicked = block
    }

    /** List-style view holder backed by [AdapterStyleListBinding]. */
    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {

        /**
         * Binds a [PlaylistWithSongs] item to the list-style row.
         *
         * @param item        The playlist data to display.
         * @param isLightBind When true, skips expensive operations such as image loading.
         */
        fun bind(item: PlaylistWithSongs, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(item.playlist.name)
            binding.secondaryDetail.text = binding.root.context.getString(R.string.x_songs, item.songs.size)
            binding.tertiaryDetail.text = item.playlist.description.ifNullOrBlank(getString(R.string.not_available))
            if (isLightBind) return
            item.songs.firstOrNull()?.let { binding.cover.loadArtCoverWithPayload(it) }
            binding.container.setOnClickListener {
                onPlaylistClicked?.invoke(item.playlist)
            }
            binding.container.setOnLongClickListener {
                onPlaylistLongClicked?.invoke(item, binding.cover)
                true
            }
        }
    }

    /** Grid-style view holder backed by [AdapterStyleGridBinding]. */
    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {

        /**
         * Binds a [PlaylistWithSongs] item to the grid-style tile.
         *
         * @param item        The playlist data to display.
         * @param isLightBind When true, skips expensive operations such as image loading.
         */
        fun bind(item: PlaylistWithSongs, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(item.playlist.name)
            binding.secondaryDetail.text = binding.root.context.getString(R.string.x_songs, item.songs.size)
            binding.tertiaryDetail.text = item.playlist.description.ifNullOrBlank(getString(R.string.not_available))
            if (isLightBind) return
            item.songs.firstOrNull()?.let { binding.albumArt.loadArtCoverWithPayload(it) }
            binding.container.setOnClickListener {
                onPlaylistClicked?.invoke(item.playlist)
            }
            binding.container.setOnLongClickListener {
                onPlaylistLongClicked?.invoke(item, binding.albumArt)
                true
            }
        }
    }

    /** Label-style view holder backed by [AdapterStyleLabelsBinding]. */
    inner class LabelHolder(val binding: AdapterStyleLabelsBinding) : VerticalListViewHolder(binding.root) {

        /**
         * Binds a [PlaylistWithSongs] item to the label-style row.
         *
         * @param item        The playlist data to display.
         * @param isLightBind When true, skips expensive operations such as image loading.
         */
        fun bind(item: PlaylistWithSongs, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(item.playlist.name)
            binding.secondaryDetail.text = binding.root.context.getString(R.string.x_songs, item.songs.size)
            binding.tertiaryDetail.text = item.playlist.description.ifNullOrBlank(getString(R.string.not_available))
            if (isLightBind) return
            binding.container.setOnClickListener {
                onPlaylistClicked?.invoke(item.playlist)
            }
            binding.container.setOnLongClickListener {
                onPlaylistLongClicked?.invoke(item, null)
                true
            }
        }
    }
}
