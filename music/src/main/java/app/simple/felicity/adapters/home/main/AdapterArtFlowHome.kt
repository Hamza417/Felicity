package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.ArtFlowSliderAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterHomeArtflowBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.ArtFlowData

/**
 * Top-level [RecyclerView.Adapter] for the ArtFlow home screen.
 *
 * <p>Each row renders an [ArtFlowData] section as a [app.simple.felicity.decorations.pager.FelicitySlider]
 * backed by an [ArtFlowSliderAdapter]. The auto-slide is started immediately after the adapter
 * is attached and paused when the view is recycled.</p>
 *
 * @author Hamza417
 */
class AdapterArtFlowHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return Holder(AdapterHomeArtflowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is Holder) {
            val item = data[position]
            val sliderAdapter = ArtFlowSliderAdapter(item)

            holder.binding.title.text = holder.binding.title.context.getString(item.title)
            holder.binding.felicitySlider.setAdapter(sliderAdapter)
            holder.binding.felicitySlider.start()

            sliderAdapter.setOnItemClickListener { itemPosition ->
                item.position = itemPosition
                adapterArtFlowHomeCallbacks?.onClicked(
                        holder.binding.container,
                        position,
                        itemPosition)
            }

            if (item.position >= 0) {
                holder.binding.felicitySlider.setCurrentItem(item.position, smoothScroll = false)
                holder.binding.container.transitionName = item.items[item.position].toString()
            }

            holder.binding.title.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onPanelItemClicked(item.title, it)
            }

            holder.binding.container.setOnClickListener {
                item.position = holder.binding.felicitySlider.getCurrentItem()
                adapterArtFlowHomeCallbacks?.onClicked(holder.binding.container, position)
            }
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        if (holder is Holder) {
            holder.binding.felicitySlider.stop()
        }
    }

    inner class Holder(val binding: AdapterHomeArtflowBinding) : VerticalListViewHolder(binding.root)

    /**
     * Registers a callback for slider-row and panel-title click events.
     *
     * @param adapterArtFlowHomeCallbacks The callback implementation to attach.
     */
    fun setAdapterArtFlowHomeCallbacks(adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks) {
        this.adapterArtFlowHomeCallbacks = adapterArtFlowHomeCallbacks
    }

    companion object {
        interface AdapterArtFlowHomeCallbacks : GeneralAdapterCallbacks {
            fun onClicked(view: View, position: Int, itemPosition: Int)
            fun onClicked(view: View, position: Int)
        }
    }
}


