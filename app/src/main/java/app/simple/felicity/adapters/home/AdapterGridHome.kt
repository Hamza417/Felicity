package app.simple.felicity.adapters.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridHomeBinding
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.Audio

class AdapterGridHome(private val data: ArrayList<Pair<Int, ArrayList<Audio>>>) :
    RecyclerView.Adapter<AdapterGridHome.Holder>() {

    private var adapterGridHomeBinding: AdapterGridHomeBinding? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        adapterGridHomeBinding =
            AdapterGridHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(adapterGridHomeBinding!!.root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(adapterGridHomeBinding!!)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        fun bind(adapterGridHomeBinding: AdapterGridHomeBinding) {
            adapterGridHomeBinding.categoryTitle.text =
                adapterGridHomeBinding.root.context.getString(data[bindingAdapterPosition].first)

            val spannedGridLayoutManager =
                SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, 3)

            spannedGridLayoutManager.spanSizeLookup =
                SpannedGridLayoutManager.SpanSizeLookup { position ->
                    if (position % 7 == 0) {
                        SpanSize(2, 2)
                    } else {
                        SpanSize(1, 1)
                    }
                }

            adapterGridHomeBinding.artGrid.setHasFixedSize(true)
            adapterGridHomeBinding.artGrid.layoutManager = spannedGridLayoutManager
            adapterGridHomeBinding.artGrid.adapter =
                AdapterGridArt(data[bindingAdapterPosition].second)

            adapterGridHomeBinding.artGrid.post {
                adapterGridHomeBinding.artGrid.layoutParams.height =
                    spannedGridLayoutManager.getTotalHeight() +
                            adapterGridHomeBinding.artGrid.paddingTop +
                            adapterGridHomeBinding.artGrid.paddingBottom
                adapterGridHomeBinding.artGrid.requestLayout()
            }
        }
    }
}