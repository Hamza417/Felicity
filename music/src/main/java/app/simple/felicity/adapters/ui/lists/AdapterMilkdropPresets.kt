package app.simple.felicity.adapters.ui.lists

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterMilkdropPresetBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.milkdrop.PresetManager
import app.simple.felicity.milkdrop.models.MilkdropPreset

/**
 * [RecyclerView.Adapter] that displays a flat sorted list of Milkdrop preset names.
 *
 * The currently active preset is highlighted with a visible check indicator.
 * Tapping any row invokes [onPresetClicked] and marks that row as selected.
 *
 * Call [submitList] to update the data set and [setSelectedPath] to move the selection
 * indicator without re-submitting the full list.
 *
 * @param onPresetClicked Callback invoked on the main thread with the tapped [MilkdropPreset].
 *
 * @author Hamza417
 */
class AdapterMilkdropPresets(
        private val onPresetClicked: (MilkdropPreset) -> Unit
) : RecyclerView.Adapter<AdapterMilkdropPresets.ViewHolder>() {

    private var presets: List<MilkdropPreset> = emptyList()

    /** Asset path of the currently selected preset; drives the check indicator visibility. */
    private var selectedPath: String = ""

    /**
     * Replaces the current data set and triggers a full redraw.
     *
     * @param list The new sorted preset list.
     */
    fun submitList(list: List<MilkdropPreset>) {
        presets = list
        notifyDataSetChanged()
    }

    /**
     * Moves the selection indicator from the previously selected row to the row whose
     * [MilkdropPreset.path] matches [path].
     *
     * Only the two affected rows are re-bound, keeping the scroll position stable.
     *
     * @param path Asset path of the newly selected preset.
     */
    fun setSelectedPath(path: String) {
        val oldIndex = presets.indexOfFirst { it.path == selectedPath }
        selectedPath = path
        val newIndex = presets.indexOfFirst { it.path == path }
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterMilkdropPresetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(presets[position])
    }

    override fun getItemCount(): Int = presets.size

    inner class ViewHolder(
            private val binding: AdapterMilkdropPresetBinding
    ) : VerticalListViewHolder(binding.root) {
        fun bind(preset: MilkdropPreset) {
            // Build "folder/name" where the "folder/" portion is grayed out
            // so related presets visually cluster together when sorted by path.
            val relativePath = preset.path
                .removePrefix("${PresetManager.PRESETS_ROOT}/")
                .removeSuffix(".milk")

            val lastSlash = relativePath.lastIndexOf('/')
            if (lastSlash >= 0) {
                val folderPart = relativePath.substring(0, lastSlash + 1)
                val spannable = SpannableString(relativePath)
                spannable.setSpan(
                        ForegroundColorSpan(Color.GRAY),
                        0, folderPart.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.presetName.text = spannable
            } else {
                binding.presetName.text = preset.name
            }

            binding.presetName.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0,
                    if (preset.path == selectedPath) R.drawable.ic_ring_12dp else 0,
                    0
            )
            binding.container.setOnClickListener { onPresetClicked(preset) }
        }
    }
}

