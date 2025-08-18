package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterDividerBinding
import app.simple.felicity.databinding.AdapterHeaderHomeBinding
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.Element

class AdapterSimpleHome(private val data: List<Element>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterHeaderHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

            RecyclerViewUtils.TYPE_ITEM -> {
                Holder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

            RecyclerViewUtils.TYPE_DIVIDER -> {
                Divider(AdapterDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

            else -> {
                throw RuntimeException("there is no type that matches the type $viewType + make sure your using types correctly")
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Holder -> {
                val position = position - 2
                holder.binding.icon.setImageResource(data[position].icon)
                holder.binding.title.text = holder.context.getString(data[position].title)
                holder.binding.container.transitionName = holder.context.getString(data[position].title)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[position], position, holder.binding.container)
                }
            }

            is Header -> {

            }
        }
    }

    override fun getItemCount(): Int {
        return data.size.plus(2)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> {
                RecyclerViewUtils.TYPE_HEADER
            }

            1 -> {
                RecyclerViewUtils.TYPE_DIVIDER
            }

            else -> {
                RecyclerViewUtils.TYPE_ITEM
            }
        }
    }

    inner class Holder(val binding: AdapterHomeSimpleBinding) : VerticalListViewHolder(binding.root) {
        init {

        }
    }

    inner class Header(val binding: AdapterHeaderHomeBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.search.setOnClickListener {
                adapterSimpleHomeCallbacks?.onSearchClicked(it)
            }

            binding.settings.setOnClickListener {
                adapterSimpleHomeCallbacks?.onMenuClicked(it)
            }
        }
    }

    inner class Divider(binding: AdapterDividerBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterSimpleHomeCallbacks(adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks) {
        this.adapterSimpleHomeCallbacks = adapterSimpleHomeCallbacks
    }

    companion object {
        private const val TAG = "AdapterSimpleHome"

        interface AdapterSimpleHomeCallbacks : GeneralAdapterCallbacks {
            fun onItemClicked(element: Element, position: Int, view: View)
        }
    }
}
