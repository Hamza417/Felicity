package app.simple.felicity.adapters.ui.lists.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterAlbumsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.albumcover.AlbumCoverUtils.loadAlbumCover
import app.simple.felicity.repository.models.Album
import com.bumptech.glide.Glide

class AdapterDefaultAlbums(initial: List<Album>) :
        RecyclerView.Adapter<AdapterDefaultAlbums.Holder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private var albums = mutableListOf<Album>().apply { addAll(initial) }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return albums[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // Try to acquire a pre-inflated view; fallback to normal synchronous inflate
        return Holder(AdapterAlbumsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = albums[position]
        holder.binding.title.setTextOrUnknown(album.name)
        holder.binding.artists.setTextOrUnknown(album.artist)
        holder.binding.count.setTextOrUnknown(holder.context.getString(R.string.x_songs, album.songCount))

        holder.binding.albumArt.loadAlbumCover(album)

        holder.binding.container.setOnLongClickListener {
            generalAdapterCallbacks?.onAlbumLongClicked(albums, position, it)
            true
        }

        holder.binding.container.setOnClickListener {
            generalAdapterCallbacks?.onAlbumClicked(albums, holder.bindingAdapterPosition, it)
        }
    }

    override fun getItemCount(): Int = albums.size

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    inner class Holder(val binding: AdapterAlbumsBinding) : VerticalListViewHolder(binding.root)
}
