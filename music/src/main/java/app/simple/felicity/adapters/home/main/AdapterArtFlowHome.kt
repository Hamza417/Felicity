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

        if (position == 0) {
            val params = holder.binding.title.layoutParams as ViewGroup.MarginLayoutParams
            // params.topMargin = StatusBarHeight.getStatusBarHeight(holder.binding.title.context.resources)
            holder.binding.title.layoutParams = params
        } else {
            holder.binding.title.setPadding(
                    holder.binding.title.paddingLeft,
                    holder.binding.title.paddingTop,
                    holder.binding.title.paddingRight,
                    holder.binding.title.paddingBottom
            )
        }

        holder.binding.container.setOnClickListener {
            adapterArtFlowHomeCallbacks?.onClicked(it, position)
        }
    }

    inner class Holder(val binding: AdapterHomeBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterArtFlowHomeCallbacks(adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks) {
        this.adapterArtFlowHomeCallbacks = adapterArtFlowHomeCallbacks
    }

    companion object {
        interface AdapterArtFlowHomeCallbacks {
            fun onClicked(view: View, position: Int)
        }
    }
}
