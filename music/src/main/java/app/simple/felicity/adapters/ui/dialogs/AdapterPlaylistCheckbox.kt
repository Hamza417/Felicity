package app.simple.felicity.adapters.ui.dialogs

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPlaylistCheckboxBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.repository.models.Playlist

/**
 * Adapter that presents a flat checkbox list of [Playlist] rows inside the
 * "Add to Playlist" dialog. Each item shows the playlist name and its current
 * song count. A pre-selected set of playlist IDs (playlists that already contain
 * the target song) is highlighted with their checkbox checked on first bind.
 *
 * @param playlists        Full list of available playlists.
 * @param preCheckedIds    Set of playlist IDs that should start checked (i.e. the
 *                         target audio is already a member of those playlists).
 * @param songCounts       Map of playlist ID to the number of songs it currently contains.
 *
 * @author Hamza417
 */
class AdapterPlaylistCheckbox(
        private var playlists: List<Playlist>,
        preCheckedIds: Set<Long> = emptySet(),
        private var songCounts: Map<Long, Int> = emptyMap()
) : RecyclerView.Adapter<AdapterPlaylistCheckbox.ViewHolder>() {


    /** Mutable snapshot of which playlist IDs are currently checked. */
    private val checkedIds: MutableSet<Long> = preCheckedIds.toMutableSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
                AdapterPlaylistCheckboxBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
        )
    }

    override fun getItemCount(): Int = playlists.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    /**
     * Returns the set of playlist IDs that are currently checked.
     */
    fun getCheckedIds(): Set<Long> = checkedIds.toSet()

    /**
     * Replaces the playlist list and song counts in-place while preserving the user's
     * existing checkbox selections. IDs belonging to playlists that no longer exist are
     * pruned from the checked set automatically.
     *
     * @param newPlaylists  The updated list of playlists to display.
     * @param newSongCounts The updated map of playlist ID to song count.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newPlaylists: List<Playlist>, newSongCounts: Map<Long, Int>) {
        this.playlists = newPlaylists
        this.songCounts = newSongCounts
        val validIds = newPlaylists.map { it.id }.toSet()
        checkedIds.retainAll(validIds)
        notifyDataSetChanged()
    }

    /**
     * Updates only the song count map and refreshes all visible items.
     *
     * @param counts The new map of playlist ID to song count.
     */
    @Suppress("unused")
    @SuppressLint("NotifyDataSetChanged")
    fun updateSongCounts(counts: Map<Long, Int>) {
        this.songCounts = counts
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: AdapterPlaylistCheckboxBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.name.text = playlist.name
            binding.count.text = binding.root.context.getString(
                    R.string.x_songs, songCounts[playlist.id] ?: 0
            )

            // Silence the checkbox listener before programmatically setting checked state
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.setChecked(checkedIds.contains(playlist.id))

            binding.checkbox.setOnCheckedChangeListener { checked ->
                if (checked) checkedIds.add(playlist.id) else checkedIds.remove(playlist.id)
            }

            binding.container.setOnClickListener {
                binding.checkbox.toggle()
            }
        }
    }
}
