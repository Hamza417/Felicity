package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.databinding.AdapterHeaderArtFlowBinding
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import com.smarteist.autoimageslider.SliderViewAdapter

class HeaderArtFlowAdapter(private val data: ArtFlowData<Any>)
    : SliderViewAdapter<HeaderArtFlowAdapter.ArtFlowViewHolder>() {

    inner class ArtFlowViewHolder(val binding: AdapterHeaderArtFlowBinding) : ViewHolder(binding.root)

    override fun getCount(): Int = data.items.size

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        val binding = AdapterHeaderArtFlowBinding.inflate(inflater, parent, false)
        return ArtFlowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtFlowViewHolder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            holder.binding.art.loadArtCover(
                    item,
                    skipCache = false,
                    darken = true,
                    crop = true
            )
        }
    }
}
