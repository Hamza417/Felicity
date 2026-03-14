package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterDashboardExpandBinding
import app.simple.felicity.databinding.AdapterHomeSimpleGridBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element

/**
 * Adapter for the panel navigation grid shown in the dashboard browse section.
 *
 * Renders a fixed list of [Element] items using the same grid item layout as the
 * simple home screen and appends an eighth expand item at the end. The expand item
 * allows the user to open the full panel list in the simple home screen.
 *
 * @param panels The list of seven panel elements to display before the expand item.
 * @author Hamza417
 */
class AdapterDashboardPanels(
        private val panels: List<Element>
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var callbacks: AdapterDashboardPanelsCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_EXPAND -> ExpandHolder(
                    AdapterDashboardExpandBinding.inflate(inflater, parent, false))
            else -> PanelHolder(
                    AdapterHomeSimpleGridBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = panels.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position < panels.size) VIEW_TYPE_PANEL else VIEW_TYPE_EXPAND
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is PanelHolder -> {
                val element = panels[position]
                holder.binding.icon.setImageResource(element.iconResId)
                holder.binding.title.text = holder.itemView.context.getString(element.titleResId)
                holder.binding.container.setOnClickListener {
                    callbacks?.onPanelClicked(panels[holder.bindingAdapterPosition])
                }
            }
            is ExpandHolder -> {
                holder.binding.container.setOnClickListener {
                    callbacks?.onExpandClicked()
                }
            }
        }
    }

    /**
     * Sets the callbacks used to respond to panel and expand item clicks.
     *
     * @param callbacks The callback implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardPanelsCallbacks) {
        this.callbacks = callbacks
    }

    inner class PanelHolder(val binding: AdapterHomeSimpleGridBinding) :
            VerticalListViewHolder(binding.root)

    inner class ExpandHolder(val binding: AdapterDashboardExpandBinding) :
            VerticalListViewHolder(binding.root)

    companion object {
        private const val VIEW_TYPE_PANEL = 0
        private const val VIEW_TYPE_EXPAND = 1

        /**
         * Callback interface for panel grid interactions.
         */
        interface AdapterDashboardPanelsCallbacks {
            /** Called when the user taps a panel navigation item. */
            fun onPanelClicked(element: Element)

            /** Called when the user taps the expand item to open the full panel list. */
            fun onExpandClicked()
        }
    }
}

