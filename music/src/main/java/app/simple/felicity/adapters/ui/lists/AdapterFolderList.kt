package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterFolderListHeaderBinding
import app.simple.felicity.databinding.AdapterFolderListItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils

/**
 * Adapter for showing a list of saved folder paths (included or excluded).
 *
 * Position 0 is always the header row — it has the panel title, a short description,
 * and the + button to add a new path. The actual path rows start at position 1.
 *
 * Each path row shows the folder name and full path, plus a remove (×) button
 * so the user can ditch paths they no longer need.
 *
 * @param titleText The title shown in the header (e.g. "Included Folders").
 * @param summaryText A short description shown below the title.
 * @param paths The current list of folder paths to display.
 * @param onAdd Called when the user taps the + button in the header.
 * @param onRemove Called when the user taps the remove button on a row.
 *
 * @author Hamza417
 */
class AdapterFolderList(
        private val titleText: String,
        private val summaryText: String,
        private var paths: List<String> = emptyList(),
        private val onAdd: () -> Unit,
        private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                val binding = AdapterFolderListHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
                Header(binding)
            }
            else -> {
                val binding = AdapterFolderListItemBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
                Holder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Header -> {
                holder.binding.title.text = titleText
                holder.binding.summary.text = summaryText
                holder.binding.add.setOnClickListener { onAdd() }
            }
            is Holder -> {
                // Position 0 is the header, so real data starts at index (position - 1).
                val path = paths[position - 1]
                val file = java.io.File(path)

                holder.binding.name.text = file.name.ifEmpty { path }
                holder.binding.path.text = path

                holder.binding.remove.setOnClickListener { onRemove(path) }
            }
        }
    }

    override fun getItemCount(): Int = paths.size + 1 // +1 for the header

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) RecyclerViewUtils.TYPE_HEADER else RecyclerViewUtils.TYPE_ITEM
    }

    /**
     * Swap in a fresh list of paths, for example after the user adds or removes one.
     */
    fun submitList(newPaths: List<String>) {
        paths = newPaths
        notifyDataSetChanged()
    }

    inner class Holder(val binding: AdapterFolderListItemBinding) : VerticalListViewHolder(binding.root)
    inner class Header(val binding: AdapterFolderListHeaderBinding) : VerticalListViewHolder(binding.root)
}
