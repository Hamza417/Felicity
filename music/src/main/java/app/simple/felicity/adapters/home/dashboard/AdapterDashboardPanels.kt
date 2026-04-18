package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterDashboardPanelBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel

/**
 * Adapter for the panel navigation grid shown in the dashboard browse section.
 *
 * Displays all [panels] at once using the same grid item layout as the Simple Home
 * screen. There is no expand/collapse button; every panel is always visible.
 *
 * @param panels The complete list of [Panel] items to display in the grid.
 * @author Hamza417
 */
class AdapterDashboardPanels(
        private val panels: List<Panel>
) : RecyclerView.Adapter<AdapterDashboardPanels.PanelHolder>() {

    private var callbacks: AdapterDashboardPanelsCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PanelHolder {
        return PanelHolder(
                AdapterDashboardPanelBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = panels.size

    override fun onBindViewHolder(holder: PanelHolder, position: Int) {
        val panel = panels[position]
        holder.binding.title.setStartDrawable(panel.iconResId)
        holder.binding.title.text = holder.itemView.context.getString(panel.titleResId)
        holder.binding.title.setOnClickListener {
            callbacks?.onPanelClicked(panels[holder.bindingAdapterPosition])
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

    companion object {
        /**
         * Callback interface for panel grid interactions.
         */
        interface AdapterDashboardPanelsCallbacks {
            /** Called when the user taps a panel navigation item. */
            fun onPanelClicked(panel: Panel)
        }
    }
}
