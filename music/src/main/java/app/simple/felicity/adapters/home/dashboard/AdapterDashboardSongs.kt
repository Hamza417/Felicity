package app.simple.felicity.adapters.home.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Audio

/**
 * Reusable horizontal list adapter for [Audio] items shown in the dashboard carousels.
 *
 * Renders each song with its album art, title, and artist name using the standard
 * carousel item layout. Used for the recently played, recently added, and favorites
 * sections of the dashboard.
 *
 * @param songs The initial list of songs to display.
 * @author Hamza417
 */
class AdapterDashboardSongs(
        private var songs: List<Audio>
) : RecyclerView.Adapter<AdapterDashboardSongs.Holder>() {

    private var callbacks: AdapterDashboardSongsCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterCarouselBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = songs[position]
        holder.binding.art.loadArtCoverWithPayload(song)
        holder.binding.title.text = song.title
            ?: holder.itemView.context.getString(R.string.unknown)
        holder.binding.artist.text = song.artist
            ?: holder.itemView.context.getString(R.string.unknown)
        holder.binding.container.setOnClickListener {
            callbacks?.onSongClicked(songs.toMutableList(), holder.bindingAdapterPosition)
        }
    }

    /**
     * Replaces the current data set with [newSongs] and refreshes the list.
     *
     * @param newSongs The updated list of songs to display.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSongs: List<Audio>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    /**
     * Sets the callbacks used to respond to song item clicks.
     *
     * @param callbacks The callback implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardSongsCallbacks) {
        this.callbacks = callbacks
    }

    inner class Holder(val binding: AdapterCarouselBinding) :
            HorizontalListViewHolder(binding.root)

    companion object {
        /**
         * Callback interface for song item interactions in the dashboard carousels.
         */
        interface AdapterDashboardSongsCallbacks {
            /**
             * Called when the user taps a song card.
             *
             * @param songs    The full list backing the carousel, used to initialize the play queue.
             * @param position The index of the tapped song within [songs].
             */
            fun onSongClicked(songs: MutableList<Audio>, position: Int)
        }
    }
}

