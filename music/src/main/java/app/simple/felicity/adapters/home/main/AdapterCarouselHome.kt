package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.AdapterCarouselItems
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterCentralHomeHeaderBinding
import app.simple.felicity.databinding.AdapterHomeCarouselBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.singletons.CarouselScrollStateStore
import app.simple.felicity.models.ArtFlowData

class AdapterCarouselHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterCarouselCallbacks: AdapterCarouselCallbacks? = null
    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER ->
                Header(AdapterCentralHomeHeaderBinding.inflate(inflater, parent, false))
            RecyclerViewUtils.TYPE_ITEM ->
                Holder(AdapterHomeCarouselBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
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

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is Holder) {
            val item = data[position.minus(1)]
            val adapter = AdapterCarouselItems(item)
            adapter.stateRestorationPolicy = StateRestorationPolicy.ALLOW
            holder.binding.recyclerView.setUniqueKey(holder.binding.title.context.getString(item.title))
            holder.binding.title.text = holder.binding.title.context.getString(item.title)
            holder.binding.recyclerView.setHasFixedSize(true)
            holder.binding.recyclerView.layoutManager = LinearLayoutManager(holder.binding.title.context, RecyclerView.HORIZONTAL, false)
            holder.binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
            holder.binding.recyclerView.adapter = adapter
            holder.binding.container.transitionName = holder.binding.title.context.getString(item.title)

            adapter.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                override fun onClicked(view: View, position: Int) {
                    CarouselScrollStateStore.savePosition(holder.binding.title.context.getString(item.title), position)
                    adapterCarouselCallbacks?.onSubItemClicked(view, holder.bindingAdapterPosition, position)
                }
            })

            holder.binding.container.setOnClickListener {
                adapterCarouselCallbacks?.onClicked(it, holder.bindingAdapterPosition)
            }
        }
    }

    inner class Holder(val binding: AdapterHomeCarouselBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterCentralHomeHeaderBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.menu.setOnClickListener {
                generalAdapterCallbacks?.onMenuClicked(it)
            }

            binding.search.setOnClickListener {
                generalAdapterCallbacks?.onSearchClicked(it)
            }
        }
    }

    fun setAdapterCarouselHomeCallbacks(callbacks: AdapterCarouselCallbacks) {
        this.adapterCarouselCallbacks = callbacks
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    companion object {
        interface AdapterCarouselCallbacks {
            fun onSubItemClicked(view: View, position: Int, itemPosition: Int)
            fun onClicked(view: View, position: Int)
        }
    }
}
