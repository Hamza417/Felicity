package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.glide.albumcover.AlbumCoverUtils.loadAlbumCover
import app.simple.felicity.glide.artistcover.ArtistCoverUtils.loadArtistCover
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song

class AdapterGridArt(private val data: ArtFlowData<Any>) :
        RecyclerView.Adapter<AdapterGridArt.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterGridImageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    holder.binding.art.loadSongCover(
                            song = item,
                            blur = false,
                            roundedCorners = false,
                            skipCache = true)
                }
                is Album -> {
                    holder.binding.art.loadAlbumCover(
                            album = item,
                            blur = false,
                            roundedCorners = false,
                            skipCache = true)
                }
                is Artist -> {
                    holder.binding.art.loadArtistCover(
                            artist = item,
                            blur = false,
                            roundedCorners = false,
                            skipCache = true)
                }
                is Genre -> {
                    holder.binding.art.loadGenreCover(
                            item,
                            blur = false,
                            roundedCorners = false,
                            skipCache = true)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return data.items.size.coerceAtMost(9)
    }

    override fun getItemId(position: Int): Long {
        return data.title.toLong()
    }

    inner class Holder(val binding: AdapterGridImageBinding) : RecyclerView.ViewHolder(binding.root)

    fun randomize() {
        for (i in 0 until itemCount) {
            // Notify position change
            notifyItemChanged(i)
        }
    }
}
