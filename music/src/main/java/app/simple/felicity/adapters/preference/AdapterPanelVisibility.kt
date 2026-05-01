package app.simple.felicity.adapters.preference

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPanelVisibilityBinding
import app.simple.felicity.databinding.AdapterPanelVisibilityHeaderBinding
import app.simple.felicity.databinding.AdapterPreferenceSubHeaderBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.Preference

/**
 * A dedicated adapter for the Panel Visibility preferences screen. It knows how
 * to display three kinds of rows: a big title header at the top, section sub-headers
 * that divide the list into groups, and the actual on/off toggle rows.
 *
 * Think of this as a tightly focused crew — it does one job and does it well,
 * rather than being a jack-of-all-trades like the generic adapter.
 *
 * @param preferences The full ordered list of preference items, including the header
 *                    and all sub-headers, built by [PanelVisibility][app.simple.felicity.ui.preferences.sub.PanelVisibility].
 * @author Hamza417
 */
class AdapterPanelVisibility(
        private val preferences: List<Preference>
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> Header(
                    AdapterPanelVisibilityHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_SUB_HEADER -> SubHeader(
                    AdapterPreferenceSubHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_SWITCH -> Switch(
                    AdapterPanelVisibilityBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val preference = preferences[position]
        when (holder) {
            is Header -> {
                // The big friendly title at the very top of the screen.
                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(R.string.panel_visibility_summary)
            }
            is SubHeader -> {
                // A small label that groups related toggles together.
                holder.binding.title.setText(preference.title)
            }
            is Switch -> {
                holder.binding.title.setText(preference.title)

                // Icons are optional — skip setting one if the preference didn't supply it.
                if (preference.icon != 0) {
                    holder.binding.icon.setImageResource(preference.icon)
                }

                // Restore the saved state without triggering the listener — otherwise
                // we'd accidentally write back the same value on every bind, which is wasteful.
                holder.binding.switchToggle.setChecked(
                        preference.valueAsBooleanProvider ?: false,
                        animateThumb = false
                )

                holder.binding.switchToggle.setOnCheckedChangeListener { switch, _ ->
                    preference.onPreferenceAction?.invoke(switch) { /* no-op */ }
                }
            }
        }
    }

    override fun getItemCount(): Int = preferences.size

    override fun getItemViewType(position: Int): Int {
        return when (preferences[position].type) {
            PreferenceType.HEADER -> VIEW_TYPE_HEADER
            PreferenceType.SUB_HEADER -> VIEW_TYPE_SUB_HEADER
            PreferenceType.SWITCH -> VIEW_TYPE_SWITCH
            else -> throw IllegalArgumentException("Unsupported type at position $position")
        }
    }

    inner class Header(val binding: AdapterPanelVisibilityHeaderBinding) : VerticalListViewHolder(binding.root)

    inner class SubHeader(val binding: AdapterPreferenceSubHeaderBinding) : VerticalListViewHolder(binding.root)

    inner class Switch(val binding: AdapterPanelVisibilityBinding) : VerticalListViewHolder(binding.root) {
        init {
            // Clipping needs to be off so the switch thumb shadow doesn't get clipped
            // at the row edges — a small detail that makes a big visual difference.
            binding.root.clipChildren = false
            binding.root.clipToPadding = false
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SUB_HEADER = 1
        private const val VIEW_TYPE_SWITCH = 2
    }
}




