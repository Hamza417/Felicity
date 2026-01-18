package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.shared.utils.ViewUtils.gone

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
            holder.binding.art.loadArtCoverWithPayload(item)

            when (item) {
                is Song -> {
                    holder.binding.title.text = item.title ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Artist -> {
                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.gone()
                }
                is Genre -> {
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