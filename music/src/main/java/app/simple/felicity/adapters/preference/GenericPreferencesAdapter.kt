package app.simple.felicity.adapters.preference

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterPreferenceDialogBinding
import app.simple.felicity.databinding.AdapterPreferenceHeaderBinding
import app.simple.felicity.databinding.AdapterPreferencePanelBinding
import app.simple.felicity.databinding.AdapterPreferencePopupBinding
import app.simple.felicity.databinding.AdapterPreferenceSliderBinding
import app.simple.felicity.databinding.AdapterPreferenceSubHeaderBinding
import app.simple.felicity.databinding.AdapterPreferenceSwitchBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.Preference

class GenericPreferencesAdapter(private val preferences: List<Preference>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                Header(AdapterPreferenceHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_SUB_HEADER -> {
                SubHeader(AdapterPreferenceSubHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_POPUP -> {
                Popup(AdapterPreferencePopupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_DIALOG -> {
                Dialog(AdapterPreferenceDialogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_SLIDER -> {
                Slider(AdapterPreferenceSliderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_PANEL -> {
                Panel(AdapterPreferencePanelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            VIEW_TYPE_SWITCH -> {
                Switch(AdapterPreferenceSwitchBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        when (holder) {
            is Header -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
            }
            is SubHeader -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
            }
            is Popup -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(preference.summary)
                holder.binding.icon.setImageResource(preference.icon)

                holder.binding.popup.setOnClickListener {
                    preference.onPreferenceAction?.invoke(it) {
                        holder.binding.popup.text = preference.valueAsStringProvider ?: ""
                    }
                }

                holder.binding.popup.text = preference.valueAsStringProvider ?: ""
            }
            is Dialog -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(preference.summary)
                holder.binding.icon.setImageResource(preference.icon)
                holder.binding.root.parent?.requestLayout()

                holder.binding.container.setOnClickListener {
                    preference.onPreferenceAction?.invoke(it) {
                        /* no-op */
                    }
                }
            }
            is Slider -> {
                val preference = preferences[position]
                val seekbarState = preference.valueAsSeekbarStateProvider
                    ?: throw IllegalStateException("SeekbarState cannot be null for slider preference")

                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(preference.summary)
                holder.binding.icon.setImageResource(preference.icon)
                holder.binding.slider.setProgress(seekbarState.position, false)
                holder.binding.slider.setDefaultProgress(seekbarState.default)
                holder.binding.slider.setMax(seekbarState.max)
                holder.binding.slider.setMin(seekbarState.min)

                holder.binding.slider.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
                    override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                        if (fromUser) {
                            preference.onPreferenceAction?.invoke(seekbar) {
                                /* no-op */
                            }
                        }
                    }
                })
            }
            is Panel -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(preference.summary)
                holder.binding.icon.setImageResource(preference.icon)

                holder.binding.container.setOnClickListener {
                    preference.onPreferenceAction?.invoke(it) {
                        /* no-op */
                    }
                }
            }
            is Switch -> {
                val preference = preferences[position]
                holder.binding.title.setText(preference.title)
                holder.binding.summary.setText(preference.summary)
                holder.binding.icon.setImageResource(preference.icon)
                holder.binding.switchToggle.isChecked = preference.valueAsBooleanProvider ?: false

                holder.binding.switchToggle.setOnCheckedChangeListener { switch, isChecked ->
                    preference.onPreferenceAction?.invoke(switch) {
                        /* no-op */
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return preferences.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return when (preferences[position].type) {
            PreferenceType.HEADER -> VIEW_TYPE_HEADER
            PreferenceType.SUB_HEADER -> VIEW_TYPE_SUB_HEADER
            PreferenceType.POPUP -> VIEW_TYPE_POPUP
            PreferenceType.SWITCH -> VIEW_TYPE_SWITCH
            PreferenceType.CHECKBOX -> VIEW_TYPE_CHECKBOX
            PreferenceType.SLIDER -> VIEW_TYPE_SLIDER
            PreferenceType.DIALOG -> VIEW_TYPE_DIALOG
            PreferenceType.PANEL -> VIEW_TYPE_PANEL
            else -> throw IllegalArgumentException("Unknown view type at position $position")
        }
    }

    inner class Popup(val binding: AdapterPreferencePopupBinding) : VerticalListViewHolder(binding.root)

    inner class Dialog(val binding: AdapterPreferenceDialogBinding) : VerticalListViewHolder(binding.root)

    inner class Slider(val binding: AdapterPreferenceSliderBinding) : VerticalListViewHolder(binding.root)

    inner class Panel(val binding: AdapterPreferencePanelBinding) : VerticalListViewHolder(binding.root)

    inner class Switch(val binding: AdapterPreferenceSwitchBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.root.clipToPadding = false
            binding.root.clipChildren = false
        }
    }

    inner class SubHeader(val binding: AdapterPreferenceSubHeaderBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterPreferenceHeaderBinding) : VerticalListViewHolder(binding.root)

    companion object {
        private const val TAG = "GenericPreferencesAdapter"

        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_SWITCH = 1
        const val VIEW_TYPE_CHECKBOX = 2
        const val VIEW_TYPE_POPUP = 3
        const val VIEW_TYPE_SLIDER = 4
        const val VIEW_TYPE_PANEL = 5
        const val VIEW_TYPE_SUB_HEADER = 6
        const val VIEW_TYPE_DIALOG = 7
    }
}