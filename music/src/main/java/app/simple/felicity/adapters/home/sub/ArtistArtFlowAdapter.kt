package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.databinding.AdapterArtistArtFlowBinding
import app.simple.felicity.glide.artistcover.ArtistCoverUtils.loadArtistCover
import app.simple.felicity.glide.filedescriptorcover.DescriptorCoverUtils.loadFromDescriptor
import app.simple.felicity.glide.genres.GenreCoverUtils.loadGenreCover
import app.simple.felicity.glide.uricover.UriCoverUtils.loadFromUri
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtistArtFlowAdapter(private val data: ArtFlowData<Any>, private val metadata: Boolean = true)
    : SliderViewAdapter<ArtistArtFlowAdapter.Holder>() {

    inner class Holder(val binding: AdapterArtistArtFlowBinding) : ViewHolder(binding.root)

    override fun getCount(): Int = data.items.size

    override fun onCreateViewHolder(parent: ViewGroup?): Holder {
        val inflater = LayoutInflater.from(parent?.context)
        val binding = AdapterArtistArtFlowBinding.inflate(inflater, parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    holder.binding.art.loadFromDescriptor(item.uri, roundedCorners = false, blur = false, skipCache = false, crop = true)
                }
                is Album -> {
                    holder.binding.art.loadFromUri(item.artworkUri, roundedCorners = false, blur = false, skipCache = false)
                }
                is Artist -> {
                    holder.binding.art.loadArtistCover(item, roundedCorners = false, blur = false, skipCache = false)
                }
                is Genre -> {
                    holder.binding.art.loadGenreCover(item, roundedCorners = false, blur = false, skipCache = false)
                }
            }
        }
    }
}
