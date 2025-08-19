package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.ArtFlowAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterFlowHomeHeaderBinding
import app.simple.felicity.databinding.AdapterHomeArtflowBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.models.ArtFlowData

class AdapterArtFlowHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {
    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterFlowHomeHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                Holder(AdapterHomeArtflowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                throw IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun getItemCount(): Int = data.size.plus(1)

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is Holder) {
            val position = position.minus(1)
            val item = data[position]
            val adapter = ArtFlowAdapter(item)
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
                item.position = holder.binding.imageSlider.currentPagePosition
                adapterArtFlowHomeCallbacks?.onClicked(holder.binding.container, position, item.position)
            }

            holder.binding.container.setOnClickListener {
                item.position = holder.binding.imageSlider.currentPagePosition
                adapterArtFlowHomeCallbacks?.onClicked(holder.binding.container, position)
            }

            holder.binding.title.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onPanelItemClicked(item.title, it)
            }
        } else if (holder is Header) {
            holder.binding.container.background = null
        } else {
            throw IllegalArgumentException("Invalid ViewHolder type")
        }
    }

    inner class Holder(val binding: AdapterHomeArtflowBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterFlowHomeHeaderBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.menu.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onMenuClicked(it)
            }

            binding.search.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onSearchClicked(it)
            }
        }
    }

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
