package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterEqualizerPresetBinding
import app.simple.felicity.databinding.AdapterPresetsHeaderBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.decorations.utils.TextViewUtils.clearStartDrawable
import app.simple.felicity.decorations.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.viewmodels.dialogs.EqualizerPresetsViewModel

/**
 * [ListAdapter] that populates the equalizer preset panel with one row per preset.
 *
 * Every row displays the preset name, a small "Built-in" badge for factory presets,
 * and a [app.simple.felicity.decorations.equalizer.EqualizerWaveView] that gives the user
 * an instant visual preview of the EQ curve shape before they commit to applying it.
 * Think of it as a "try before you apply" experience — in glanceable waveform form.
 *
 * Parametric EQ presets show a "PEQ" badge instead of the wave to indicate their type,
 * since a proper frequency-response curve would need full biquad math per band — a nice
 * future addition, but not required for the preset list to be functional and clear.
 *
 * The currently active preset (identified by [selectedPresetId]) shows an accent-colored
 * indicator so users always know which preset is in effect, even after scrolling around.
 *
 * @param onPresetClicked      Called when the user taps a row to apply that preset.
 * @param onOptionClicked  Called when the user taps the options button; receives both the
 *                             preset and the view so the caller can anchor a popup near it.
 *
 * @author Hamza417
 */
class AdapterEqualizerPresets(
        private val onPresetClicked: (EqualizerPreset) -> Unit,
        private val onOptionClicked: (EqualizerPreset, View, Float, Float) -> Unit = { _, _, _, _ -> },
        private val onSavedPresetsClicked: () -> Unit = {},
        private val onHeadphoneProfilesClicked: () -> Unit = {},
        private val onImportClicked: () -> Unit = {},
) : ListAdapter<EqualizerPreset, VerticalListViewHolder>(DIFF_CALLBACK) {

    /** The id of the currently applied preset, or -1 if none is active. */
    private var selectedPresetId: Long = -1L

    private var currentListType: EqualizerPresetsViewModel.ListType =
        EqualizerPresetsViewModel.ListType.SAVED_PRESETS

    override fun submitList(list: List<EqualizerPreset>?) {
        val newList = if (list.isNullOrEmpty()) {
            emptyList()
        } else {
            listOf(HEADER_PRESET) + list
        }
        super.submitList(newList)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).id == HEADER_PRESET.id) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    fun setListType(listType: EqualizerPresetsViewModel.ListType) {
        currentListType = listType
        if (itemCount > 0) {
            notifyItemChanged(0) // update header
        }
    }

    /**
     * Updates the selected-preset indicator without re-submitting the entire list.
     * Only the two affected rows are rebound, which keeps the scroll position perfectly stable.
     *
     * @param presetId The id of the newly selected preset, or -1 to clear the selection.
     */
    fun setSelectedPresetId(presetId: Long) {
        val oldId = selectedPresetId
        selectedPresetId = presetId

        // Rebind only the rows that changed so there is no jarring full-list flash.
        val oldIndex = currentList.indexOfFirst { it.id == oldId }
        val newIndex = currentList.indexOfFirst { it.id == presetId }
        if (oldIndex >= 0) notifyItemChanged(oldIndex + 1)
        if (newIndex >= 0) notifyItemChanged(newIndex + 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterPresetsHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                PresetHolder(AdapterEqualizerPresetBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalStateException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is PresetHolder) {
            holder.bind(getItem(position))
        } else if (holder is Header) {
            holder.binding.title.text = holder.getString(R.string.presets)
            holder.binding.summary.text = holder.getString(R.string.presets_desc)

            holder.binding.savedPresets.setUseAccentColor(
                    currentListType == EqualizerPresetsViewModel.ListType.SAVED_PRESETS)
            holder.binding.headphoneProfiles.setUseAccentColor(
                    currentListType == EqualizerPresetsViewModel.ListType.HEADPHONE_PROFILES)

            holder.binding.savedPresets.setOnClickListener {
                onSavedPresetsClicked()
            }

            holder.binding.headphoneProfiles.setOnClickListener {
                onHeadphoneProfilesClicked()
            }

            holder.binding.importPreset.setOnClickListener {
                onImportClicked()
            }
        }
    }

    inner class Header(val binding: AdapterPresetsHeaderBinding) : VerticalListViewHolder(binding.root)

    inner class PresetHolder(
            private val binding: AdapterEqualizerPresetBinding
    ) : VerticalListViewHolder(binding.root) {

        fun bind(preset: EqualizerPreset) {
            binding.presetName.text = preset.name

            binding.presetType.text = if (preset.isPEQ()) {
                getString(R.string.parametric_eq)
            } else {
                getString(R.string.graphic_eq)
            }

            if (preset.isBuiltIn) {
                binding.presetState.visible(animate = false)
            } else {
                binding.presetState.gone(animate = false)
            }

            // Highlight the row whose preset is currently applied.
            if (preset.id == selectedPresetId) {
                binding.presetName.setStartDrawable(R.drawable.ic_dot_12dp)
            } else {
                binding.presetName.clearStartDrawable()
            }

            // Feed the wave view the appropriate data so it renders the correct EQ curve shape.
            if (preset.isPEQ()) {
                binding.equalizerWave.setPeqBands(preset.getPeqBands())
            } else {
                binding.equalizerWave.setGains(preset.getBandGains())
            }

            binding.container.setOnClickListener {
                onPresetClicked(preset)
            }

            var lastTouchX = 0f
            var lastTouchY = 0f

            binding.container.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                false
            }

            binding.container.setOnLongClickListener {
                onOptionClicked(preset, binding.container, lastTouchX, lastTouchY)
                true
            }
        }
    }

    companion object {
        /**
         * Easily distinguishable placeholder preset for header, we can just use position + 1 but this is easier.
         */
        val HEADER_PRESET = EqualizerPreset(id = -100L, name = "HEADER", bandGainsRaw = "")

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EqualizerPreset>() {
            override fun areItemsTheSame(oldItem: EqualizerPreset, newItem: EqualizerPreset): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: EqualizerPreset, newItem: EqualizerPreset): Boolean {
                return oldItem == newItem
            }
        }
    }
}
