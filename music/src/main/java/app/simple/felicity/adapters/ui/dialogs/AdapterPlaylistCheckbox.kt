package app.simple.felicity.adapters.ui.dialogs

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPlaylistCheckboxBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.toggles.OnCheckedChangeListener
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
 *
 * @author Hamza417
 */
class AdapterPlaylistCheckbox(
        private val playlists: List<Playlist>,
        private val preCheckedIds: Set<Long> = emptySet()
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

    inner class ViewHolder(private val binding: AdapterPlaylistCheckboxBinding) :
            VerticalListViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(playlist: Playlist) {
            binding.name.text = playlist.name
            binding.count.text = binding.root.context.getString(R.string.x_songs, 0)

            // Silence the checkbox listener before programmatically setting checked state
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.setChecked(checkedIds.contains(playlist.id))

            binding.checkbox.setOnCheckedChangeListener(object : OnCheckedChangeListener {
                override fun onCheckedChanged(checked: Boolean) {
                    if (checked) checkedIds.add(playlist.id) else checkedIds.remove(playlist.id)
                }
            })

            binding.container.setOnClickListener {
                binding.checkbox.toggle()
            }
        }
    }

    /**
     * Updates the song count label for a playlist once the cross-ref count is known.
     *
     * @param playlistId The playlist whose count should be updated.
     * @param count      The number of songs currently in the playlist.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateSongCount(playlistId: Long, count: Int) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) notifyItemChanged(index, count)
    }
}

