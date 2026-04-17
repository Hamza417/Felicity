package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPathPickerHeaderBinding
import app.simple.felicity.databinding.AdapterPathPickerItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.viewmodels.panels.PathPickerViewModel.PathItem

/**
 * Adapter for the path picker recycler view.
 *
 * Position 0 is always the header row — it shows the current directory path and
 * a confirm (✓) button that selects that directory as the result without drilling deeper.
 * The actual folder and audio file rows start at position 1.
 *
 * Tapping a folder row drills into it. Long-pressing any row selects it as the result.
 * Tapping an audio file row selects it directly.
 *
 * @param onNavigate Called when the user taps a folder to browse into it.
 * @param onSelect Called when the user wants to select a path (folder or audio file).
 * @param onConfirm Called when the user taps the check button in the header to confirm the current directory.
 *
 * @author Hamza417
 */
class AdapterPathPicker(
        private var items: List<PathItem> = emptyList(),
        private var currentPath: String? = null,
        private val onNavigate: (PathItem) -> Unit,
        private val onSelect: (PathItem) -> Unit,
        private val onConfirm: () -> Unit
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                val binding = AdapterPathPickerHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
                Header(binding)
            }
            else -> {
                val binding = AdapterPathPickerItemBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
                Holder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Header -> {
                holder.binding.currentPath.text = currentPath
                    ?: holder.itemView.context.getString(R.string.storage_root)
                holder.binding.confirm.setOnClickListener { onConfirm() }
            }
            is Holder -> {
                // Position 0 is the header, so real items start at index (position - 1).
                val item = items[position - 1]

                holder.binding.name.text = item.displayName
                holder.binding.path.text = item.file.absolutePath

                // Folders get a folder icon, audio files get an audio file icon.
                holder.binding.icon.setImageResource(
                        if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_audio_file
                )

                holder.binding.container.setOnClickListener {
                    if (item.isDirectory) {
                        onNavigate(item)
                    } else {
                        onSelect(item)
                    }
                }

                // Long-press lets the user pick a folder without navigating into it.
                holder.binding.container.setOnLongClickListener {
                    onSelect(item)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size + 1 // +1 for the header

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) RecyclerViewUtils.TYPE_HEADER else RecyclerViewUtils.TYPE_ITEM
    }

    /**
     * Push a fresh list of directory contents into the adapter.
     * Called every time the ViewModel navigates into a new directory.
     */
    fun submitList(newItems: List<PathItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * Update the path label shown in the header row.
     * Called whenever the ViewModel's [currentPath] changes.
     */
    fun updateCurrentPath(path: String?) {
        currentPath = path
        notifyItemChanged(0)
    }

    inner class Holder(val binding: AdapterPathPickerItemBinding) : VerticalListViewHolder(binding.root)
    inner class Header(val binding: AdapterPathPickerHeaderBinding) : VerticalListViewHolder(binding.root)
}
