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
import app.simple.felicity.glide.songcover.SongCoverUtils.loadBlurredBWSongCover
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Song
import app.simple.felicity.utils.ArrayUtils.getTwoRandomIndices

class AdapterGridHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

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

                binding.artGrid.setHasFixedSize(true)
                binding.artGrid.layoutManager = spannedGridLayoutManager
                binding.artGrid.adapter = AdapterGridArt(data[position.minus(1)])
                binding.artGrid.scheduleLayoutAnimation()

                binding.artGrid.post {
                    binding.artGrid.layoutParams.height =
                        spannedGridLayoutManager.getTotalHeight() +
                                binding.artGrid.paddingTop +
                                binding.artGrid.paddingBottom
                    binding.artGrid.requestLayout()
                }
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

    inner class Holder(val binding: AdapterGridHomeBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterSpannedHomeHeaderBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.menu.setOnClickListener {
                generalAdapterCallbacks?.onMenuClicked(it)
            }

            binding.search.setOnClickListener {
                generalAdapterCallbacks?.onSearchClicked(it)
            }

            findRandomSongFromData()?.let { binding.headerArt.loadBlurredBWSongCover(it) }
            binding.subContainer.background = null
        }
    }

    private fun findRandomSongFromData(): Song? {
        data.forEach {
            if (it.items.random() is Song) {
                Log.d("AdapterGridHome", "Found a random song in data")
                return it.items.random() as Song
            }
        }

        return null
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }
}
