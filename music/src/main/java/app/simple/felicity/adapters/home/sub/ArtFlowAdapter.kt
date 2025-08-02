package app.simple.felicity.adapters.home.sub

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterArtFlowBinding
import app.simple.felicity.glide.genres.GenreCoverModel
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: ArtFlowData<Any>) : SliderViewAdapter<ArtFlowAdapter.ArtFlowViewHolder>() {

    inner class ArtFlowViewHolder(val binding: AdapterArtFlowBinding) : ViewHolder(binding.root) {
        fun getContext(): Context = binding.root.context
    }

    override fun getCount(): Int = data.items.size

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        val binding = AdapterArtFlowBinding.inflate(inflater, parent, false)
        return ArtFlowViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ArtFlowViewHolder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    Glide.with(viewHolder.binding.art)
                        .asBitmap()
                        .load(UriCoverModel(
                                viewHolder.getContext(),
                                item.artworkUri!!
                        ))
                        .dontTransform()
                        .dontAnimate()
                        .into(viewHolder.binding.art)

                    viewHolder.binding.title.text = item.title ?: viewHolder.getContext().getString(R.string.unknown)
                    viewHolder.binding.artist.text = item.artist ?: viewHolder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    Glide.with(viewHolder.binding.art)
                        .asBitmap()
                        .load(UriCoverModel(
                                viewHolder.getContext(),
                                (data.items[position] as Album).artworkUri!!
                        ))
                        .dontTransform()
                        .dontAnimate()
                        .into(viewHolder.binding.art)

                    viewHolder.binding.title.text = item.name ?: viewHolder.getContext().getString(R.string.unknown)
                    viewHolder.binding.artist.text = item.artist ?: viewHolder.getContext().getString(R.string.unknown)
                }
                is Genre -> {
                    Glide.with(viewHolder.binding.art)
                        .asBitmap()
                        .load(GenreCoverModel(
                                viewHolder.getContext(),
                                (data.items[position] as Genre).id,
                                (data.items[position] as Genre).name
                                    ?: viewHolder.getContext().getString(R.string.unknown)
                        ))
                        .transform(CenterCrop())
                        .dontAnimate()
                        .into(viewHolder.binding.art)

                    viewHolder.binding.title.text = item.name ?: viewHolder.getContext().getString(R.string.unknown)
                }
            }
        }
    }
}
