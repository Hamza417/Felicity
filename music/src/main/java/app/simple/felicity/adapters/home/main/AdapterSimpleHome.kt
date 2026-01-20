package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel.Companion.Element

class AdapterSimpleHome(private val data: List<Element>) : RecyclerView.Adapter<AdapterSimpleHome.Holder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.icon.setImageResource(data[position].iconResId)
        holder.binding.title.text = holder.context.getString(data[position].titleResId)

        holder.binding.container.setOnClickListener {
            adapterSimpleHomeCallbacks?.onItemClicked(data[position], position, holder.binding.container)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class Holder(val binding: AdapterHomeSimpleBinding) : VerticalListViewHolder(binding.root) {
        init {

        }
    }

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
