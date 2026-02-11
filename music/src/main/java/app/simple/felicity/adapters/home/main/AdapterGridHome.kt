package app.simple.felicity.adapters.home.main

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterGridHomeBinding
import app.simple.felicity.databinding.AdapterSpannedHomeHeaderBinding
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.utils.ArrayUtils.getTwoRandomIndices

class AdapterGridHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSpannedHomeCallbacks: AdapterSpannedHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER ->
                Header(AdapterSpannedHomeHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            RecyclerViewUtils.TYPE_ITEM ->
                Holder(AdapterGridHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else ->
                throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            with(holder) {
                val randomPossibleAlternateSpanPositions = intArrayOf(1, 2, 3, 4, 5, 7).getTwoRandomIndices()
                val spannedGridLayoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, 3)

                spannedGridLayoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
                    if (position in randomPossibleAlternateSpanPositions) {
                        SpanSize(2, 2)
                    } else {
                        SpanSize(1, 1)
                    }
                }

                val adapter = AdapterGridArt(data[position.minus(1)])
                binding.artGrid.setHasFixedSize(true)
                binding.artGrid.layoutManager = spannedGridLayoutManager
                binding.artGrid.adapter = adapter
                binding.artGrid.scheduleLayoutAnimation()

                binding.artGrid.post {
                    binding.artGrid.layoutParams.height =
                        spannedGridLayoutManager.getTotalHeight() +
                                binding.artGrid.paddingTop +
                                binding.artGrid.paddingBottom
                    binding.artGrid.requestLayout()
                }

                adapter.setCallbacks(object : AdapterGridArt.Companion.AdapterGridArtCallbacks {
                    override fun onItemClicked(items: List<Any>, position: Int) {
                        adapterSpannedHomeCallbacks?.onItemClicked(items, position)
                    }

                    override fun onItemLongClicked(item: Any) {
                        adapterSpannedHomeCallbacks?.onItemLongClicked(item)
                    }

                    override fun onButtonClicked(title: Int) {
                        adapterSpannedHomeCallbacks?.onButtonClicked(title)
                    }
                })
            }
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

    override fun getItemId(position: Int): Long {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER.toLong()
        } else {
            data[position - 1].title.hashCode().toLong()
        }
    }

    inner class Holder(val binding: AdapterGridHomeBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterSpannedHomeHeaderBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.menu.setOnClickListener {
                adapterSpannedHomeCallbacks?.onMenuClicked(it)
            }

            binding.search.setOnClickListener {
                adapterSpannedHomeCallbacks?.onSearchClicked(it)
            }

            // findRandomSongFromData()?.let { binding.headerArt.loadBlurredBWSongCover(it) }
            binding.subContainer.background = null
        }
    }

    private fun findRandomSongFromData(): Audio? {
        data.forEach {
            if (it.items.random() is Audio) {
                Log.d("AdapterGridHome", "Found a random song in data")
                return it.items.random() as Audio
            }
        }

        return null
    }

    fun setAdapterSpannedHomeCallbacks(callbacks: AdapterSpannedHomeCallbacks) {
        this.adapterSpannedHomeCallbacks = callbacks
    }

    companion object {
        interface AdapterSpannedHomeCallbacks : GeneralAdapterCallbacks {
            fun onItemClicked(items: List<Any>, position: Int)
            fun onItemLongClicked(item: Any)
            fun onButtonClicked(title: Int)
        }
    }
}
