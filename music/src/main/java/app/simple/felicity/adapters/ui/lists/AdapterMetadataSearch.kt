package app.simple.felicity.adapters.ui.lists

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterMetadataSearchBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.MusicBrainzRecordingResult

/**
 * RecyclerView adapter for the MusicBrainz recording search results shown in
 * [app.simple.felicity.ui.subpanels.MetadataSearch].
 *
 * Each item shows the track title, credited artist, and the album name paired with
 * the track duration. Tapping an item triggers [onItemClick] so the search screen can
 * package the result and deliver it back to the metadata editor via the Fragment Result API.
 *
 * @param onItemClick called with the tapped [MusicBrainzRecordingResult] when the user picks a result.
 *
 * @author Hamza417
 */
class AdapterMetadataSearch(
        private val onItemClick: (MusicBrainzRecordingResult) -> Unit
) : RecyclerView.Adapter<AdapterMetadataSearch.MetadataViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<MusicBrainzRecordingResult>() {
        override fun areItemsTheSame(oldItem: MusicBrainzRecordingResult, newItem: MusicBrainzRecordingResult) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MusicBrainzRecordingResult, newItem: MusicBrainzRecordingResult) =
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

    private val results: List<MusicBrainzRecordingResult>
        get() = differ.currentList

    init {
        setHasStableIds(true)
    }

    /**
     * Uses the MusicBrainz MBID as the stable id so the RecyclerView can animate
     * list changes smoothly without re-drawing items that didn't change.
     */
    override fun getItemId(position: Int): Long = results[position].id.hashCode().toLong()

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
    fun updateResults(newResults: List<MusicBrainzRecordingResult>) {
        differ.submitList(newResults.toList())
    }

    inner class MetadataViewHolder(private val binding: AdapterMetadataSearchBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(recording: MusicBrainzRecordingResult) {
            binding.trackName.text = recording.title.orEmpty()

            // Combine all credited artists into a single readable string.
            binding.artistName.text = recording.artistCredit
                ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                ?.joinToString(", ")
                .orEmpty()

            // Pick the first release as the most representative album for this recording.
            val album = recording.releases?.firstOrNull()?.title?.takeIf { it.isNotBlank() }

            // Convert milliseconds to a human-friendly "m:ss" string if available.
            val duration = recording.length?.let { ms ->
                DateUtils.formatElapsedTime(ms / 1000)
            }

            binding.albumDuration.text = listOfNotNull(album, duration).joinToString(" · ")

            binding.container.setOnClickListener {
                onItemClick(recording)
            }
        }
    }
}
