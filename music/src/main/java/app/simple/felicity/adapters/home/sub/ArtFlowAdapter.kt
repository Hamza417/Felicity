package app.simple.felicity.adapters.home.sub

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterArtFlowBinding
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: ArtFlowData<Any>, private val startGravity: Boolean = true)
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

            holder.binding.art.loadArtCover(
                    item,
                    skipCache = true,
                    crop = true,
                    darken = false
            )

            when (item) {
                is Audio -> {
                    holder.binding.title.text = item.title ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Artist -> {
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                }
                is Genre -> {
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                }
            }

            // alternate text gravity on every alternate item
            holder.binding.title.gravity = if (startGravity) {
                android.view.Gravity.START
            } else {
                android.view.Gravity.END
            }

            holder.binding.artist.gravity = holder.binding.title.gravity

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
