package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterFolderListHeaderBinding
import app.simple.felicity.databinding.AdapterFolderListItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils

/**
 * Adapter for showing a list of saved SAF folder URIs.
 *
 * Position 0 is always the header row — it has the panel title, a short description,
 * and the + button to add a new folder. The actual folder rows start at position 1.
 *
 * Each folder row shows the human-readable folder name and its URI as the path,
 * plus a remove (×) button so the user can revoke access to folders they no longer need.
 *
 * @param titleText The title shown in the header.
 * @param summaryText A short description shown below the title.
 * @param uris The current list of SAF tree URI strings to display.
 * @param onAdd Called when the user taps the + button in the header.
 * @param onRemove Called with the URI string when the user taps the remove button on a row.
 *
 * @author Hamza417
 */
class AdapterFolderList(
        private var uris: List<String> = emptyList(),
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
                holder.binding.title.text = holder.getString(R.string.manage_music_folders)
                holder.binding.summary.text = holder.getString(R.string.manage_music_folders_desc)
                holder.binding.add.setOnClickListener { onAdd() }
            }
            is Holder -> {
                // Position 0 is the header, so real data starts at index (position - 1).
                val uriString = uris[position - 1]
                val uri = uriString.toUri()

                // Try to get a friendly folder name from the SAF document tree.
                // If DocumentFile can't resolve it (e.g. the folder was deleted),
                // we fall back to showing the raw URI so the user can still remove it.
                val docFile = try {
                    DocumentFile.fromTreeUri(holder.itemView.context, uri)
                } catch (_: Exception) {
                    null
                }

                holder.binding.name.text = docFile?.name ?: uriString
                holder.binding.path.text = uriString

                holder.binding.remove.setOnClickListener { onRemove(uriString) }
            }
        }
    }

    override fun getItemCount(): Int = uris.size + 1 // +1 for the header

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) RecyclerViewUtils.TYPE_HEADER else RecyclerViewUtils.TYPE_ITEM
    }

    /**
     * Swap in a fresh list of URI strings, for example after the user adds or removes one.
     */
    fun submitList(newUris: List<String>) {
        uris = newUris
        notifyDataSetChanged()
    }

    inner class Holder(val binding: AdapterFolderListItemBinding) : VerticalListViewHolder(binding.root)
    inner class Header(val binding: AdapterFolderListHeaderBinding) : VerticalListViewHolder(binding.root)
}
