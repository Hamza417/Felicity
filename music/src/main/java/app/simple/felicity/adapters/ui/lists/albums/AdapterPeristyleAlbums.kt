package app.simple.felicity.adapters.ui.lists.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterAlbumsPeristyleBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.albumcover.AlbumCoverUtils.loadAlbumCover
import app.simple.felicity.repository.models.Album

class AdapterPeristyleAlbums(private val albums: List<Album>) : RecyclerView.Adapter<AdapterPeristyleAlbums.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterAlbumsPeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding.root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(AdapterAlbumsPeristyleBinding.bind(holder.itemView))
    }

    override fun getItemCount(): Int {
        return albums.size
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        var binding: AdapterAlbumsPeristyleBinding? = null

        fun bind(binding: AdapterAlbumsPeristyleBinding) {
            this.binding = binding
            binding.albumArt.loadAlbumCover(albums[bindingAdapterPosition], crop = true, roundedCorners = false, blurShadow = false, skipCache = false)
            binding.title.text = albums[bindingAdapterPosition].name
        }
    }
}
