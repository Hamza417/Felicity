package app.simple.felicity.adapters.home.sub

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.core.utils.ViewUtils.gone
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.artistcover.ArtistCoverUtils.loadArtistCover
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.glide.songcover.SongCoverUtils.loadSongCover
import app.simple.felicity.glide.uricover.UriCoverUtils.loadFromUri
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song

class AdapterCarouselItems(private val data: ArtFlowData<Any>) : RecyclerView.Adapter<AdapterCarouselItems.Holder>() {

    private var adapterCarouselCallbacks: AdapterCarouselCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterCarouselBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int {
        return data.items.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    holder.binding.art.loadSongCover(item)
                    holder.binding.title.text = item.title ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    holder.binding.art.loadFromUri(item.artworkUri ?: Uri.EMPTY)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Artist -> {
                    holder.binding.art.loadArtistCover(artist = item)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.gone()
                }
                is Genre -> {
                    holder.binding.art.loadGenreCover(item)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.gone()
                    holder.binding.container.transitionName = item.toString()
                }
            }

            holder.binding.container.setOnClickListener {
                adapterCarouselCallbacks?.onClicked(holder.binding.container, position)
            }
        }
    }

    fun setAdapterCarouselCallbacks(callbacks: AdapterCarouselCallbacks) {
        this.adapterCarouselCallbacks = callbacks
    }

    inner class Holder(val binding: AdapterCarouselBinding) : HorizontalListViewHolder(binding.root)

    companion object {
        interface AdapterCarouselCallbacks {
            fun onClicked(view: View, position: Int)
        }
    }
}