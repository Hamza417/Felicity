package app.simple.felicity.adapters.player

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.ItemBookmarkRowBinding
import app.simple.felicity.repository.models.AudioBookmark

/**
 * Shows a flat list of bookmarks for a single song inside the bookmarks' dialog.
 *
 * Each row displays the bookmark's formatted timestamp. When [showDeleteButton] is true, a
 * small trash icon appears at the end of every row. Tapping a row fires [onTimestampClicked],
 * and tapping the trash icon fires [onDeleteClicked]. Removing an item calls
 * [notifyItemRemoved] so the default RecyclerView transition plays smoothly.
 *
 * @author Hamza417
 */
class AdapterBookmarkTimestamps(
        private val items: MutableList<AudioBookmark>,
        private val showDeleteButton: Boolean,
        private val onTimestampClicked: (AudioBookmark) -> Unit,
        private val onDeleteClicked: ((AudioBookmark, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<AdapterBookmarkTimestamps.BookmarkHolder>() {

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkHolder {
        val binding = ItemBookmarkRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookmarkHolder(binding)
    }

    override fun onBindViewHolder(holder: BookmarkHolder, position: Int) {
        holder.bind(items[position])
    }

    /**
     * Removes a bookmark from the list at the given adapter position and plays
     * the default item-removal animation provided by RecyclerView.
     */
    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun isEmpty(): Boolean = items.isEmpty()

    inner class BookmarkHolder(private val binding: ItemBookmarkRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(bookmark: AudioBookmark) {
            val label = DateUtils.formatElapsedTime(bookmark.timestampMs / 1000L)
            binding.bookmarkTimestamp.text = label

            binding.bookmarkTimestamp.setOnClickListener {
                onTimestampClicked(bookmark)
            }

            if (showDeleteButton) {
                binding.bookmarkDelete.visibility = View.VISIBLE
                binding.bookmarkDelete.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_ID.toInt()) {
                        removeAt(pos)
                        onDeleteClicked?.invoke(bookmark, isEmpty())
                    }
                }
            } else {
                binding.bookmarkDelete.visibility = View.GONE
            }
        }
    }
}

