package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.databinding.AdapterArtistArtFlowBinding
import app.simple.felicity.glide.filedescriptorcover.DescriptorCoverUtils.loadFromDescriptor
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Song
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtistArtFlowAdapter(private val data: ArtFlowData<Any>)
    : SliderViewAdapter<ArtistArtFlowAdapter.Holder>() {

    inner class Holder(val binding: AdapterArtistArtFlowBinding) : ViewHolder(binding.root)

    override fun getCount(): Int = data.items.size.coerceAtMost(12)

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
                else -> {
                    holder.binding.art.loadArtCover(item, roundedCorners = false, blur = false, skipCache = false)
                }
            }
        }
    }
}
