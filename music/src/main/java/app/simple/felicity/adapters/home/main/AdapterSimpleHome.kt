package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.databinding.AdapterHomeSimpleCarouselBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel.Companion.Element

class AdapterSimpleHome(private val data: List<Element>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_CAROUSEL -> {
                CarouselHolder(AdapterHomeSimpleCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                SimpleHolder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is CarouselHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[position], position, holder.binding.container)
                }

                holder.binding.carousel.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onCarouselClicked(data[position], position, holder.binding.carousel)
                }
            }
            is SimpleHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[position], position, holder.binding.container)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (data[position].titleResId) {
            R.string.songs, R.string.albums -> VIEW_TYPE_CAROUSEL
            else -> VIEW_TYPE_SIMPLE
        }
    }

    inner class CarouselHolder(val binding: AdapterHomeSimpleCarouselBinding) : VerticalListViewHolder(binding.root)

    inner class SimpleHolder(val binding: AdapterHomeSimpleBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterSimpleHomeCallbacks(adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks) {
        this.adapterSimpleHomeCallbacks = adapterSimpleHomeCallbacks
    }

    companion object {
        private const val TAG = "AdapterSimpleHome"

        private const val VIEW_TYPE_CAROUSEL = 0
        private const val VIEW_TYPE_SIMPLE = 1

        interface AdapterSimpleHomeCallbacks : GeneralAdapterCallbacks {
            fun onItemClicked(element: Element, position: Int, view: View)
            fun onCarouselClicked(element: Element, position: Int, view: View)
        }
    }
}
