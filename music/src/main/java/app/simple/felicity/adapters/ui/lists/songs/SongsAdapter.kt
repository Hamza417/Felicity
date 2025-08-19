package app.simple.felicity.adapters.ui.lists.songs

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterSongHeaderBinding
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class SongsAdapter(private val audio: List<Song>) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1

    var currentlyPlayingSong: Song? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            val idx = audio.indexOf(value)
            if (idx != -1) {
                notifyItemChanged(idx + 1) // +1 for the header
                if (previousIndex != -1 && previousIndex != idx) {
                    notifyItemChanged(previousIndex + 1) // +1 for the header
                }
                previousIndex = idx
            } else {
                notifyDataSetChanged() // If the song is not found, refresh the entire list
            }
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
            holder.bind(audio[position - 1])

            holder.binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(audio, holder.bindingAdapterPosition - 1, it)
            }

            holder.binding.container.setDefaultBackground(currentlyPlayingSong == audio[position - 1])
        }
    }

    override fun getItemCount(): Int {
        return audio.size.plus(1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
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
        }
    }
}
