package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.sub.AdapterCarouselItems
import app.simple.felicity.databinding.AdapterHomeCarouselBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.singletons.CarouselScrollStateStore
import app.simple.felicity.models.ArtFlowData

class AdapterCarouselHome(private val data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<AdapterCarouselHome.Holder>() {

    private var adapterCarouselCallbacks: AdapterCarouselCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterHomeCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: Holder, @SuppressLint("RecyclerView") position: Int) {
        val item = data[position]
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

    inner class Holder(val binding: AdapterHomeCarouselBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterCarouselHomeCallbacks(callbacks: AdapterCarouselCallbacks) {
        this.adapterCarouselCallbacks = callbacks
    }

    companion object {
        interface AdapterCarouselCallbacks {
            fun onSubItemClicked(view: View, position: Int, itemPosition: Int)
            fun onClicked(view: View, position: Int)
        }
    }
}
