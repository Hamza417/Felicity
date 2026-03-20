package app.simple.felicity.adapters.home.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterDashboardExpandBinding
import app.simple.felicity.databinding.AdapterDashboardPanelBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel

/**
 * Adapter for the panel navigation grid shown in the dashboard browse section.
 *
 * Renders up to [firstPanels] items by default, followed by an eighth expand item.
 * When the expand item is tapped the adapter reveals the full [allPanels] list inline
 * and swaps the expand icon for a collapse icon — no navigation occurs.
 *
 * @param firstPanels The initial subset of panels to display (typically seven items).
 * @param allPanels   The complete panel list revealed after the user taps expand.
 * @author Hamza417
 */
class AdapterDashboardPanels(
        private val firstPanels: List<Panel>,
        private val allPanels: List<Panel>
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var callbacks: AdapterDashboardPanelsCallbacks? = null

    /** Whether the full panel list is currently shown. */
    private var isExpanded = false

    private val activePanels: List<Panel>
        get() = if (isExpanded) allPanels else firstPanels

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_EXPAND -> ExpandHolder(
                    AdapterDashboardExpandBinding.inflate(inflater, parent, false))
            else -> PanelHolder(
                    AdapterDashboardPanelBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = activePanels.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position < activePanels.size) VIEW_TYPE_PANEL else VIEW_TYPE_EXPAND
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is PanelHolder -> {
                val element = activePanels[position]
                holder.binding.icon.setImageResource(element.iconResId)
                holder.binding.title.text = holder.itemView.context.getString(element.titleResId)
                holder.binding.container.setOnClickListener {
                    callbacks?.onPanelClicked(activePanels[holder.bindingAdapterPosition])
                }
            }
            is ExpandHolder -> {
                if (isExpanded) {
                    holder.binding.icon.setImageResource(R.drawable.ic_fold)
                    holder.binding.title.setText(R.string.less)
                } else {
                    holder.binding.icon.setImageResource(R.drawable.ic_unfold)
                    holder.binding.title.setText(R.string.more)
                }
                holder.binding.container.setOnClickListener {
                    isExpanded = !isExpanded
                    notifyDataSetChanged()
                }
            }
        }
    }

    /**
     * Sets the callbacks used to respond to panel item clicks.
     *
     * @param callbacks The callback implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardPanelsCallbacks) {
        this.callbacks = callbacks
    }

    inner class PanelHolder(val binding: AdapterDashboardPanelBinding) :
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
            fun onPanelClicked(panel: Panel)
        }
    }
}
