package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.databinding.AdapterHomeSimpleGridBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element

class AdapterSimpleHome(private val data: MutableList<Element>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null
    private var layoutType: Int = CommonPreferencesConstants.GRID_TYPE_LIST

    fun setLayoutType(type: Int) {
        layoutType = type
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> {
                GridHolder(AdapterHomeSimpleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                SimpleHolder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is GridHolder -> {
                holder.binding.title.text = holder.context.getString(data[position].titleResId)
                holder.binding.icon.setImageResource(data[position].iconResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.container)
                }
            }
            is SimpleHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.container)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            layoutType == CommonPreferencesConstants.GRID_TYPE_GRID -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_SIMPLE
        }
    }

    inner class SimpleHolder(val binding: AdapterHomeSimpleBinding) : VerticalListViewHolder(binding.root)

    inner class GridHolder(val binding: AdapterHomeSimpleGridBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterSimpleHomeCallbacks(adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks) {
        this.adapterSimpleHomeCallbacks = adapterSimpleHomeCallbacks
    }

    companion object {
        private const val VIEW_TYPE_SIMPLE = 1
        private const val VIEW_TYPE_GRID = 2

        interface AdapterSimpleHomeCallbacks : GeneralAdapterCallbacks {
            fun onItemClicked(element: Element, position: Int, view: View)
        }
    }
}
