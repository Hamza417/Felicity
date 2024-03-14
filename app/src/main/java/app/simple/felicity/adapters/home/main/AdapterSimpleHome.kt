package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.HomeItem

class AdapterSimpleHome(private val data: ArrayList<HomeItem>) : RecyclerView.Adapter<AdapterSimpleHome.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(AdapterHomeSimpleBinding.bind(holder.itemView))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        var adapterHomeSimpleBinding: AdapterHomeSimpleBinding? = null

        fun bind(adapterHomeSimpleBinding: AdapterHomeSimpleBinding) {
            this.adapterHomeSimpleBinding = adapterHomeSimpleBinding

            adapterHomeSimpleBinding.icon.setImageResource(data[bindingAdapterPosition].icon)
            adapterHomeSimpleBinding.title.text = itemView.context.getString(data[bindingAdapterPosition].title)
        }
    }
}
