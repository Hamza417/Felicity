package app.simple.felicity.adapters.home.sub

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterArtFlowBinding
import app.simple.felicity.glide.filedescriptorcover.DescriptorCoverUtils.loadFromDescriptor
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.glide.uricover.UriCoverUtils.loadFromUri
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: ArtFlowData<Any>, private val metadata: Boolean = true)
    : SliderViewAdapter<ArtFlowAdapter.ArtFlowViewHolder>() {

    private var artFlowAdapterCallbacks: ArtFlowAdapterCallbacks? = null

    inner class ArtFlowViewHolder(val binding: AdapterArtFlowBinding) : ViewHolder(binding.root) {
        fun getContext(): Context = binding.root.context
    }

    override fun getCount(): Int = data.items.size

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        val binding = AdapterArtFlowBinding.inflate(inflater, parent, false)
        return ArtFlowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtFlowViewHolder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    holder.binding.art.loadFromDescriptor(item.uri, roundedCorners = false, blur = false, skipCache = false)
                    holder.binding.title.text = item.title ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    holder.binding.art.loadFromUri(item.artworkUri, roundedCorners = false, blur = false, skipCache = false)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Artist -> {
                    holder.binding.art.loadFromUri(item.artworkUri, roundedCorners = false, blur = false, skipCache = false)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                }
                is Genre -> {
                    holder.binding.art.loadGenreCover(item, roundedCorners = false, blur = false, skipCache = false)
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                }
            }

            holder.binding.container.setOnClickListener {
                Log.d("ArtFlowAdapter", "Item clicked at position: $position")
                artFlowAdapterCallbacks?.onClicked()
            }
        }
    }

    fun setArtFlowAdapterCallbacks(callbacks: ArtFlowAdapterCallbacks) {
        this.artFlowAdapterCallbacks = callbacks
    }

    companion object {
        private const val TAG = "ArtFlowAdapter"

        interface ArtFlowAdapterCallbacks {
            fun onClicked()
        }
    }
}
