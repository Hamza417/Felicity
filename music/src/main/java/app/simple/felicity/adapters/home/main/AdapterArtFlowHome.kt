package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.home.sub.ArtFlowAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.theme.ThemeFrameLayout
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.views.FelicityImageSlider
import app.simple.felicity.repository.models.home.Home

class AdapterArtFlowHome() : RecyclerView.Adapter<VerticalListViewHolder>() {

    private val data = arrayListOf<Home>()
    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.adapter_home, parent, false))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val h = holder as Holder
        h.title.text = h.title.context.getString(data[position].title)
        h.sliderView.setSliderAdapter(ArtFlowAdapter(data[position]))

        //        if(position == 0) {
        //            h.title.apply {
        //                // Set margin top to status bar height
        //                val params = h.title.layoutParams as ViewGroup.MarginLayoutParams
        //                params.topMargin = StatusBarHeight.getStatusBarHeight(h.title.context.resources)
        //                h.title.layoutParams = params
        //            }
        //        } else {
        //            h.title.apply {
        //                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        //            }
        //        }

        h.title.setOnClickListener {
            adapterArtFlowHomeCallbacks?.onClicked(it, position)
        }
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        val container: ThemeFrameLayout = itemView.findViewById(R.id.container)
        val sliderView: FelicityImageSlider = itemView.findViewById(R.id.imageSlider)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
    }

    fun setAdapterArtFlowHomeCallbacks(adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks) {
        this.adapterArtFlowHomeCallbacks = adapterArtFlowHomeCallbacks
    }

    fun insertData(home: Home) {
        data.removeIf {
            it.title == home.title
        }

        data.add(home)
        notifyDataSetChanged()
    }

    companion object {
        interface AdapterArtFlowHomeCallbacks {
            fun onClicked(view: View, position: Int)
        }
    }
}
