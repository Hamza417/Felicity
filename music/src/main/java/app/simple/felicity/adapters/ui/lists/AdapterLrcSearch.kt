package app.simple.felicity.adapters.ui.lists

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterLrcSearchBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.LrcLibResponse

/**
 * RecyclerView adapter for displaying a list of [LrcLibResponse] search results in
 * the [app.simple.felicity.ui.panels.LyricsSearch] panel.
 *
 * Each item shows the track name, artist name, and album with duration. Tapping an item
 * invokes [onItemClick] so the panel can download and save the selected LRC entry.
 *
 * @param onItemClick callback invoked with the tapped [LrcLibResponse].
 *
 * @author Hamza417
 */
class AdapterLrcSearch(
        private val onItemClick: (LrcLibResponse) -> Unit
) : RecyclerView.Adapter<AdapterLrcSearch.LrcViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<LrcLibResponse>() {
        override fun areItemsTheSame(oldItem: LrcLibResponse, newItem: LrcLibResponse): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LrcLibResponse, newItem: LrcLibResponse): Boolean {
            return oldItem == newItem
        }
    }

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val results: List<LrcLibResponse>
        get() = differ.currentList

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = results[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LrcViewHolder {
        val binding = AdapterLrcSearchBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return LrcViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LrcViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    /**
     * Submits a new list of [LrcLibResponse] items using [AsyncListDiffer] for efficient
     * diff-based updates.
     *
     * @param newResults the updated list of search results to display.
     */
    fun updateResults(newResults: List<LrcLibResponse>) {
        differ.submitList(newResults.toList())
    }

    inner class LrcViewHolder(private val binding: AdapterLrcSearchBinding) :
            VerticalListViewHolder(binding.root) {

        /**
         * Binds a [LrcLibResponse] to the item views and wires up the click listener.
         *
         * @param response the LRC search result to display.
         */
        fun bind(response: LrcLibResponse) {
            binding.trackName.text = response.trackName
            binding.artistName.text = response.artistName

            val album = response.albumName?.takeIf { it.isNotBlank() }
            val duration = DateUtils.formatElapsedTime(response.duration.toLong())

            binding.albumDuration.text = when {
                album != null -> "$album · $duration"
                else -> duration
            }

            binding.container.setOnClickListener {
                onItemClick(response)
            }
        }
    }
}

