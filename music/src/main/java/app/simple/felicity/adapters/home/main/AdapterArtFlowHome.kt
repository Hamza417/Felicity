package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.ArtFlowAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterHomeArtflowBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.ArtFlowData

class AdapterArtFlowHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {
    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return Holder(AdapterHomeArtflowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is Holder) {
            val item = data[position]
            val adapter = ArtFlowAdapter(item, position % 2 == 0)
            holder.binding.title.text = holder.binding.title.context.getString(item.title)
            holder.binding.imageSlider.setSliderAdapter(adapter)

            adapter.setArtFlowAdapterCallbacks(object : ArtFlowAdapter.Companion.ArtFlowAdapterCallbacks {
                override fun onClicked() {
                    item.position = holder.binding.imageSlider.currentPagePosition
                    adapterArtFlowHomeCallbacks?.onClicked(
                            holder.binding.container,
                            position,
                            holder.binding.imageSlider.currentPagePosition)
                }
            })

            if (item.position >= 0) {
                holder.binding.imageSlider.currentPagePosition = item.position
                holder.binding.container.transitionName = item.items[item.position].toString()
            }

            holder.binding.title.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onPanelItemClicked(item.title, it)
            }

            holder.binding.container.setOnClickListener {
                item.position = holder.binding.imageSlider.currentPagePosition
                adapterArtFlowHomeCallbacks?.onClicked(holder.binding.container, position)
            }
        }
    }

    inner class Holder(val binding: AdapterHomeArtflowBinding) : VerticalListViewHolder(binding.root)

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
