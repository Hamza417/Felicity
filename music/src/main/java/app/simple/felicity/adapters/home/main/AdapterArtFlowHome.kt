package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.ArtFlowAdapter
import app.simple.felicity.databinding.AdapterHomeBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.ArtFlowData

class AdapterArtFlowHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<AdapterArtFlowHome.Holder>() {
    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = data[position]
        holder.binding.title.text = holder.binding.title.context.getString(item.title)
        holder.binding.imageSlider.setSliderAdapter(ArtFlowAdapter(item))

        if (item.position >= 0) {
            holder.binding.imageSlider.currentPagePosition = item.position
            holder.binding.container.transitionName = item.items[item.position].toString()
        }

        holder.binding.imageSlider.setCurrentPageListener {
            holder.binding.container.transitionName = item.items[it].toString()
            item.position = it
        }

        holder.binding.title.setOnClickListener {
            holder.binding.container.transitionName = item.items[holder.binding.imageSlider.currentPagePosition].toString()

            adapterArtFlowHomeCallbacks?.onClicked(
                    holder.binding.container,
                    position,
                    holder.binding.imageSlider.currentPagePosition)
        }
    }

    inner class Holder(val binding: AdapterHomeBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterArtFlowHomeCallbacks(adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks) {
        this.adapterArtFlowHomeCallbacks = adapterArtFlowHomeCallbacks
    }

    companion object {
        interface AdapterArtFlowHomeCallbacks {
            fun onClicked(view: View, position: Int, itemPosition: Int)
        }
    }
}
