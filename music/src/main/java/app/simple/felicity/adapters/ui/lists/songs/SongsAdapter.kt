package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class SongsAdapter(initial: List<Song>, preInflate: Int = 0) :
        RecyclerView.Adapter<SongsAdapter.Holder>(), SlideFastScroller.FastScrollOptimizedAdapter {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1

    private var songs = mutableListOf<Song>().apply { addAll(initial) }
    private var lightBindMode = false

    var currentlyPlayingSong: Song? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { songs.indexOf(it) } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long {
        return songs[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterSongsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = songs[position]
        if (lightBindMode.not()) {
            holder.bind(song)
        } else {
            holder.lightBind()
        }

        holder.binding.container.setOnClickListener {
            generalAdapterCallbacks?.onSongClicked(songs, holder.bindingAdapterPosition, it)
        }

        holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val song = songs[position]
            holder.binding.container.setDefaultBackground(currentlyPlayingSong == song)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = songs.size

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    override fun setLightBindMode(enabled: Boolean) {
        lightBindMode = enabled
    }

    inner class Holder(val binding: AdapterSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                title.setTextOrUnknown(song.title)
                artists.setTextOrUnknown(song.artist)
                album.setTextOrUnknown(song.album)

                albumArt.loadSongCover(song)

                container.setOnLongClickListener {
                    generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, it)
                    true
                }
            }
        }

        fun lightBind() {

        }
    }

    companion object {
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}