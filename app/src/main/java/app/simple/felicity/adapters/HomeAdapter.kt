package app.simple.felicity.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.views.FelicityImageSlider
import app.simple.felicity.models.Audio

class HomeAdapter(private val data: ArrayList<ArrayList<Audio>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.adapter_home, parent, false))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val h = holder as Holder
        h.title.text = data[position][0].album
        h.sliderView.setSliderAdapter(ArtFlowAdapter(data[position]))
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        val container: FrameLayout = itemView.findViewById(R.id.container)
        val sliderView: FelicityImageSlider = itemView.findViewById(R.id.imageSlider)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
    }
}