package app.simple.felicity.adapters.ui.lists.songs

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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

    private val songs = mutableListOf<Song>().apply { addAll(initial) }

    init {
        setHasStableIds(true)
    }

    var currentlyPlayingSong: Song? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            val idx = songs.indexOf(value)
            if (idx != -1) {
                notifyItemChanged(idx + 1) // +1 for header
                if (previousIndex != -1 && previousIndex != idx) {
                    notifyItemChanged(previousIndex + 1)
                }
                previousIndex = idx
            } else {
                notifyDataSetChanged() // Song not found; full refresh fallback
            }
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
            holder.bind(songs[position - 1])

            holder.binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, holder.bindingAdapterPosition - 1, it)
            }

            holder.binding.container.setDefaultBackground(currentlyPlayingSong == songs[position - 1])
        } else if (holder is Header) {
            holder.binding.sortStyle.text = when (SongsPreferences.getSongSort()) {
                SongsPreferences.BY_TITLE -> holder.binding.root.context.getString(R.string.title)
                SongsPreferences.BY_ARTIST -> holder.binding.root.context.getString(R.string.artist)
                SongsPreferences.BY_ALBUM -> holder.binding.root.context.getString(R.string.album)
                SongsPreferences.PATH -> holder.binding.root.context.getString(R.string.path)
                SongsPreferences.BY_DATE_ADDED -> holder.binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DATE_MODIFIED -> holder.binding.root.context.getString(R.string.date_added)
                SongsPreferences.BY_DURATION -> holder.binding.root.context.getString(R.string.duration)
                SongsPreferences.BY_YEAR -> holder.binding.root.context.getString(R.string.year)
                SongsPreferences.BY_TRACK_NUMBER -> holder.binding.root.context.getString(R.string.track_number)
                SongsPreferences.BY_COMPOSER -> holder.binding.root.context.getString(R.string.composer)
                else -> holder.binding.root.context.getString(R.string.unknown)
            }
        }
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

    // DiffUtil update method
    fun updateSongs(newSongs: List<Song>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = songs.size
            override fun getNewListSize(): Int = newSongs.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                songs[oldItemPosition].id == newSongs[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                songs[oldItemPosition] == newSongs[newItemPosition]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        songs.clear()
        songs.addAll(newSongs)
        diffResult.dispatchUpdatesTo(this)
        // Recompute previousIndex in new list for currently playing song
        currentlyPlayingSong?.let { cps ->
            previousIndex = songs.indexOf(cps)
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
            binding.menu.setOnClickListener {
                generalAdapterCallbacks?.onMenuClicked(binding.menu)
            }

            binding.filter.setOnClickListener {
                generalAdapterCallbacks?.onFilterClicked(binding.filter)
            }

            binding.sortStyle.setOnClickListener {
                generalAdapterCallbacks?.onSortClicked(binding.sortStyle)
            }
        }
    }

    companion object {
        private const val HEADER_ITEM_ID = Long.MIN_VALUE // Unique stable ID for header
    }
}
