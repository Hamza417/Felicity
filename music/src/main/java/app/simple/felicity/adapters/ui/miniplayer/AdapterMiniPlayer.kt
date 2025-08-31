package app.simple.felicity.adapters.ui.miniplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterMiniPlayerAlbumArtBinding
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.repository.models.Song

class AdapterMiniPlayer(private val list: List<Song>) : RecyclerView.Adapter<AdapterMiniPlayer.Holder>() {

    private var callbacks: MiniPlayerAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterMiniPlayerAlbumArtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = list[position]
        holder.binding.albumArt.loadSongCover(song, crop = true, blur = false)
        holder.binding.title.text = song.title
        holder.binding.artist.text = song.artist

        holder.binding.container.setOnClickListener {
            callbacks?.onOpenPlayer()
        }

        holder.binding.container.setOnLongClickListener {
            callbacks?.onOpenPopupPlayer()
            true
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class Holder(val binding: AdapterMiniPlayerAlbumArtBinding) : RecyclerView.ViewHolder(binding.root)

    fun setCallbacks(callbacks: MiniPlayerAdapterCallbacks) {
        this.callbacks = callbacks
    }

    companion object {
        interface MiniPlayerAdapterCallbacks : GeneralAdapterCallbacks {
            fun onOpenPlayer()
            fun onOpenPopupPlayer()
        }
    }
}