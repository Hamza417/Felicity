package app.simple.felicity.adapters.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterFelicityMainHeaderBinding
import app.simple.felicity.databinding.AdapterGridHomeBinding
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.Audio
import app.simple.felicity.utils.ArrayUtils.getTwoRandomIndices
import app.simple.felicity.utils.RecyclerViewUtils

class AdapterGridHome(private val data: ArrayList<Pair<Int, ArrayList<Audio>>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterFelicityMainHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
            }

            RecyclerViewUtils.TYPE_ITEM -> {

                Holder(AdapterGridHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
            }

            else -> {
                throw IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            holder.bind(AdapterGridHomeBinding.bind(holder.itemView))
        }
    }

    override fun getItemCount(): Int {
        return data.size.plus(1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {

        var adapterGridHomeBinding: AdapterGridHomeBinding? = null

        fun bind(adapterGridHomeBinding: AdapterGridHomeBinding) {
            this.adapterGridHomeBinding = adapterGridHomeBinding

            adapterGridHomeBinding.categoryTitle.text = adapterGridHomeBinding.root.context.getString(data[bindingAdapterPosition.minus(1)].first)
            val randomPossibleAlternateSpanPositions = intArrayOf(1, 2, 3, 4, 5, 7, 8, 9).getTwoRandomIndices()

            val spannedGridLayoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, 3)
            spannedGridLayoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
                if (position in randomPossibleAlternateSpanPositions) {
                    SpanSize(2, 2)
                } else {
                    SpanSize(1, 1)
                }
            }

            adapterGridHomeBinding.artGrid.setHasFixedSize(true)
            adapterGridHomeBinding.artGrid.layoutManager = spannedGridLayoutManager
            adapterGridHomeBinding.artGrid.adapter = AdapterGridArt(data[bindingAdapterPosition.minus(1)].second)
            adapterGridHomeBinding.artGrid.scheduleLayoutAnimation()

            adapterGridHomeBinding.artGrid.post {
                adapterGridHomeBinding.artGrid.layoutParams.height =
                    spannedGridLayoutManager.getTotalHeight() +
                            adapterGridHomeBinding.artGrid.paddingTop +
                            adapterGridHomeBinding.artGrid.paddingBottom
                adapterGridHomeBinding.artGrid.requestLayout()
            }
        }
    }

    inner class Header(itemView: View) : VerticalListViewHolder(itemView)
}