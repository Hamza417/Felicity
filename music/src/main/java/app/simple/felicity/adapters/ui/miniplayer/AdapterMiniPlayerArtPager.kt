package app.simple.felicity.adapters.ui.miniplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterMiniPlayerAlbumArtBinding
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.repository.models.Song

class AdapterMiniPlayerArtPager(private val list: List<Song>) : RecyclerView.Adapter<AdapterMiniPlayerArtPager.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterMiniPlayerAlbumArtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = list[position]
        holder.binding.albumArt.loadSongCover(song, crop = true)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class Holder(val binding: AdapterMiniPlayerAlbumArtBinding) : RecyclerView.ViewHolder(binding.root)
}