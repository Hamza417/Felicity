package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterFolderListHeaderBinding
import app.simple.felicity.databinding.AdapterFolderListItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils

class AdapterArtistWhitelist(
        private val onAdd: () -> Unit,
        private val onRemove: (String) -> Unit,
) : ListAdapter<String, VerticalListViewHolder>(DiffCallback()) {

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
                holder.binding.title.text = holder.getString(R.string.artist_whitelist)
                holder.binding.summary.text = holder.getString(R.string.artist_whitelist_desc)
                holder.binding.add.setOnClickListener { onAdd() }
            }
            is Holder -> {
                val artistName = getItem(position)

                holder.binding.name.text = artistName
                holder.binding.path.visibility = View.GONE

                holder.binding.remove.setOnClickListener { onRemove(artistName) }
            }
        }
    }

    override fun getItemCount(): Int = currentList.size + 1

    override fun getItem(position: Int): String {
        return super.getItem(position - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) RecyclerViewUtils.TYPE_HEADER else RecyclerViewUtils.TYPE_ITEM
    }

    inner class Holder(val binding: AdapterFolderListItemBinding) : VerticalListViewHolder(binding.root)
    inner class Header(val binding: AdapterFolderListHeaderBinding) : VerticalListViewHolder(binding.root)

    private class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
