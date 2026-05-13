package app.simple.felicity.adapters.ui.lists

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterMetadataSearchBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.LrcLibResponse

/**
 * RecyclerView adapter for the metadata search results shown in
 * [app.simple.felicity.ui.subpanels.MetadataSearch].
 *
 * Each item shows the track name, artist, album + duration, and a small badge
 * whenever the result also carries synced LRC lyrics. Tapping an item triggers
 * [onItemClick] so the search screen can package the result and deliver it back
 * to the metadata editor.
 *
 * @param onItemClick called with the tapped [LrcLibResponse] when the user picks a result.
 *
 * @author Hamza417
 */
class AdapterMetadataSearch(
        private val onItemClick: (LrcLibResponse) -> Unit
) : RecyclerView.Adapter<AdapterMetadataSearch.MetadataViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<LrcLibResponse>() {
        override fun areItemsTheSame(oldItem: LrcLibResponse, newItem: LrcLibResponse) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LrcLibResponse, newItem: LrcLibResponse) =
            oldItem == newItem
    }

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) = notifyItemRangeInserted(position, count)
        override fun onRemoved(position: Int, count: Int) = notifyItemRangeRemoved(position, count)
        override fun onMoved(from: Int, to: Int) = notifyItemMoved(from, to)
        override fun onChanged(position: Int, count: Int, payload: Any?) =
            notifyItemRangeChanged(position, count, payload)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MetadataViewHolder {
        val binding = AdapterMetadataSearchBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return MetadataViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MetadataViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    /**
     * Replaces the current list with [newResults], using [AsyncListDiffer] for smooth updates.
     */
    fun updateResults(newResults: List<LrcLibResponse>) {
        differ.submitList(newResults.toList())
    }

    inner class MetadataViewHolder(private val binding: AdapterMetadataSearchBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(response: LrcLibResponse) {
            binding.trackName.text = response.trackName
            binding.artistName.text = response.artistName

            val album = response.albumName?.takeIf { it.isNotBlank() }
            val duration = DateUtils.formatElapsedTime(response.duration.toLong())
            binding.albumDuration.text = if (album != null) "$album · $duration" else duration

            // Show the "synced" badge whenever this result includes LRC timestamps.
            binding.syncedBadge.visibility =
                if (!response.syncedLyrics.isNullOrBlank()) View.VISIBLE else View.GONE

            binding.container.setOnClickListener {
                onItemClick(response)
            }
        }
    }
}

