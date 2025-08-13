package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class SongsAdapter(private val audio: List<Song>) :
        RecyclerView.Adapter<SongsAdapter.Holder>() {

    var onItemClickListener: ((Song, Int, View) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
                AdapterSongsBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(audio[position])
    }

    override fun getItemCount(): Int {
        return audio.size
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    inner class Holder(val binding: AdapterSongsBinding) :
            VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                // albumArt.transitionName = audio.fileUri
                title.setTextOrUnknown(song.title)
                artists.setTextOrUnknown(song.artist)
                album.setTextOrUnknown(song.album)

                albumArt.loadSongCover(song)
                albumArt.transitionName = song.path
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke(song, bindingAdapterPosition, binding.albumArt)
            }
        }
    }
}
