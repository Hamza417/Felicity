package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterDividerBinding
import app.simple.felicity.databinding.AdapterHeaderHomeBinding
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.HomeItem

class AdapterSimpleHome(private val data: ArrayList<HomeItem>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterHeaderHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
            }

            RecyclerViewUtils.TYPE_ITEM -> {
                Holder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
            }

            RecyclerViewUtils.TYPE_DIVIDER -> {
                Divider(AdapterDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
            }

            else -> {
                throw RuntimeException("there is no type that matches the type $viewType + make sure your using types correctly")
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Holder -> {
                holder.bind(AdapterHomeSimpleBinding.bind(holder.itemView))
            }

            is Header -> {
                holder.bind(AdapterHeaderHomeBinding.bind(holder.itemView))
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

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        var adapterHomeSimpleBinding: AdapterHomeSimpleBinding? = null

        fun bind(adapterHomeSimpleBinding: AdapterHomeSimpleBinding) {
            this.adapterHomeSimpleBinding = adapterHomeSimpleBinding
            val position = bindingAdapterPosition.minus(2)

            adapterHomeSimpleBinding.icon.setImageResource(data[position].icon)
            adapterHomeSimpleBinding.title.text = itemView.context.getString(data[position].title)
            adapterHomeSimpleBinding.container.transitionName = itemView.context.getString(data[position].title)

            adapterHomeSimpleBinding.container.setOnClickListener {
                adapterSimpleHomeCallbacks?.onItemClicked(data[position], position, adapterHomeSimpleBinding.container)
            }
        }
    }

    inner class Header(itemView: View) : VerticalListViewHolder(itemView) {
        var adapterHeaderHomeBinding: AdapterHeaderHomeBinding? = null

        fun bind(adapterHeaderHomeBinding: AdapterHeaderHomeBinding) {
            this.adapterHeaderHomeBinding = adapterHeaderHomeBinding
        }
    }

    inner class Divider(itemView: View) : VerticalListViewHolder(itemView)

    fun setAdapterSimpleHomeCallbacks(adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks) {
        this.adapterSimpleHomeCallbacks = adapterSimpleHomeCallbacks
    }

    companion object {
        private const val TAG = "AdapterSimpleHome"

        interface AdapterSimpleHomeCallbacks {
            fun onItemClicked(homeItem: HomeItem, position: Int, icon: View)
        }
    }
}
