package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterHomeSimpleBinding
import app.simple.felicity.databinding.AdapterHomeSimpleCarouselBinding
import app.simple.felicity.databinding.AdapterHomeSimpleGridBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element

class AdapterSimpleHome(private val data: MutableList<Element>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var layoutType: Int = CommonPreferencesConstants.GRID_TYPE_LIST

    fun setLayoutType(type: Int) {
        layoutType = type
        notifyItemRangeChanged(0, itemCount)
    }

    fun attachItemTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
        ) {
            override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                // Move item in the backing list and notify adapter directly
                val item = data.removeAt(from)
                data.add(to, item)
                notifyItemMoved(from, to)
                // Persist the new order via callback
                adapterSimpleHomeCallbacks?.onItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_CAROUSEL -> {
                CarouselHolder(AdapterHomeSimpleCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_GRID -> {
                GridHolder(AdapterHomeSimpleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                SimpleHolder(AdapterHomeSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is CarouselHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.container)
                }

                holder.binding.carousel.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onCarouselClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.carousel)
                }
            }
            is GridHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.container)
                }
            }
            is SimpleHolder -> {
                holder.binding.icon.setImageResource(data[position].iconResId)
                holder.binding.title.text = holder.context.getString(data[position].titleResId)

                holder.binding.container.setOnClickListener {
                    adapterSimpleHomeCallbacks?.onItemClicked(data[holder.bindingAdapterPosition], holder.bindingAdapterPosition, holder.binding.container)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            layoutType == CommonPreferencesConstants.GRID_TYPE_GRID -> VIEW_TYPE_GRID
            data[position].titleResId == R.string.songs ||
                    data[position].titleResId == R.string.albums -> VIEW_TYPE_CAROUSEL
            else -> VIEW_TYPE_SIMPLE
        }
    }

    inner class CarouselHolder(val binding: AdapterHomeSimpleCarouselBinding) : VerticalListViewHolder(binding.root)

    inner class SimpleHolder(val binding: AdapterHomeSimpleBinding) : VerticalListViewHolder(binding.root)

    inner class GridHolder(val binding: AdapterHomeSimpleGridBinding) : VerticalListViewHolder(binding.root)

    fun setAdapterSimpleHomeCallbacks(adapterSimpleHomeCallbacks: AdapterSimpleHomeCallbacks) {
        this.adapterSimpleHomeCallbacks = adapterSimpleHomeCallbacks
    }

    companion object {
        private const val VIEW_TYPE_CAROUSEL = 0
        private const val VIEW_TYPE_SIMPLE = 1
        private const val VIEW_TYPE_GRID = 2

        interface AdapterSimpleHomeCallbacks : GeneralAdapterCallbacks {
            fun onItemClicked(element: Element, position: Int, view: View)
            fun onCarouselClicked(element: Element, position: Int, view: View)
            fun onItemMoved(fromPosition: Int, toPosition: Int)
        }
    }
}
