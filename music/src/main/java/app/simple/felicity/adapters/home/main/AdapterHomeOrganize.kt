package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterHomeOrganizeBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element

/**
 * Adapter for the [app.simple.felicity.dialogs.home.HomeOrganize] bottom sheet dialog.
 * Each item exposes a drag handle that starts a drag gesture via [ItemTouchHelper].
 * Long-press drag on the whole row is intentionally disabled; only the explicit
 * handle triggers reordering, keeping the interaction deliberate and bug-free.
 *
 * @author Hamza417
 */
class AdapterHomeOrganize(private val data: MutableList<Element>) :
        RecyclerView.Adapter<AdapterHomeOrganize.OrganizeHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null

    /**
     * Attaches an [ItemTouchHelper] to both this adapter and the provided [recyclerView].
     * The helper is configured to allow vertical drag only and no swipe.
     */
    fun attachItemTouchHelper(recyclerView: RecyclerView) {
        itemTouchHelper?.attachToRecyclerView(null)

        val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val item = data.removeAt(from)
                data.add(to, item)
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            /** Long-press drag is disabled; only the drag handle initiates a drag. */
            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper!!.attachToRecyclerView(recyclerView)
    }

    /**
     * Returns a snapshot of the current item order.
     * Call this when the user confirms the new arrangement.
     */
    fun getCurrentOrder(): List<Element> = data.toList()

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: OrganizeHolder, position: Int) {
        holder.binding.icon.setImageResource(data[position].iconResId)
        holder.binding.title.text = holder.context.getString(data[position].titleResId)

        holder.binding.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrganizeHolder {
        return OrganizeHolder(
                AdapterHomeOrganizeBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
        )
    }

    override fun getItemCount(): Int = data.size

    inner class OrganizeHolder(val binding: AdapterHomeOrganizeBinding) :
            VerticalListViewHolder(binding.root)
}

