package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import app.simple.felicity.databinding.AdapterEqualizerPresetBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible

/**
 * [ListAdapter] that populates the equalizer preset panel with one row per preset.
 *
 * Every row displays the preset name, a small "Built-in" badge for factory presets,
 * and a [app.simple.felicity.decorations.views.EqualizerWaveView] that gives the user
 * an instant visual preview of the EQ curve shape before they commit to applying it.
 * Think of it as a "try before you apply" experience — in glanceable waveform form.
 *
 * The currently active preset (identified by [selectedPresetId]) shows an accent-colored
 * indicator so users always know which preset is in effect, even after scrolling around.
 *
 * @param onPresetClicked      Called when the user taps a row to apply that preset.
 * @param onOptionClicked  Called when the user long-presses a row; receives both the
 *                             preset and the view so the caller can anchor a popup near it.
 *
 * @author Hamza417
 */
class AdapterEqualizerPresets(
        private val onPresetClicked: (EqualizerPreset) -> Unit,
        private val onOptionClicked: (EqualizerPreset, View) -> Unit = { _, _ -> }
) : ListAdapter<EqualizerPreset, AdapterEqualizerPresets.ViewHolder>(DIFF_CALLBACK) {

    /** The id of the currently applied preset, or -1 if none is active. */
    private var selectedPresetId: Long = -1L

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
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterEqualizerPresetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
            private val binding: AdapterEqualizerPresetBinding
    ) : VerticalListViewHolder(binding.root) {

        fun bind(preset: EqualizerPreset) {
            binding.presetName.text = preset.name

            // Show the "Built-in" badge only for factory presets so users can tell
            // at a glance which ones came with the app and which ones they made themselves.
            if (preset.isBuiltIn) {
                binding.presetBuiltInLabel.text = binding.root.context.getString(
                        app.simple.felicity.R.string.built_in)
                binding.presetBuiltInLabel.visible()
            } else {
                binding.presetBuiltInLabel.gone()
            }

            // Highlight the row whose preset is currently applied — handy for orientation.
            if (preset.id == selectedPresetId) {
                binding.presetSelectedIndicator.text = "●"
                binding.presetSelectedIndicator.visible()
            } else {
                binding.presetSelectedIndicator.gone()
            }

            // Feed the wave view the band gains so it renders the correct EQ curve shape.
            binding.equalizerWave.setGains(preset.getBandGains())

            binding.container.setOnClickListener {
                onPresetClicked(preset)
            }

            binding.presetOptionsButton.setOnClickListener {
                onOptionClicked(preset, binding.presetOptionsButton)
                true
            }
        }
    }

    companion object {
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

