package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
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
        holder.binding.container.setOnLongClickListener {
            callbacks?.onSongLongClicked(
                    songs.toMutableList(),
                    holder.bindingAdapterPosition,
                    holder.binding.art as ImageView)
            true
        }
    }

    /**
     * Replaces the current data set with [newSongs] and dispatches granular change notifications
     * computed by [DiffUtil]. Items are identified by [Audio.id] so additions, removals, and
     * moves are all animated individually without resetting the scroll position.
     *
     * @param newSongs The updated list of songs to display.
     */
    fun updateData(newSongs: List<Audio>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = songs.size
            override fun getNewListSize() = newSongs.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                songs[oldItemPosition].id == newSongs[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                songs[oldItemPosition] == newSongs[newItemPosition]
        })
        songs = newSongs
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Randomizes the visible content of [holder] by swapping its displayed song to a
     * randomly chosen entry from the backing list. Intended for use with the
     * TiledHome-style periodic shuffle animation in the recently played carousel.
     *
     * @param holder The [Holder] whose displayed song should be replaced.
     */
    fun randomize(holder: Holder) {
        if (songs.isEmpty()) return
        val randomSong = songs.random()
        holder.binding.art.loadArtCoverWithPayload(randomSong)
        holder.binding.title.text = randomSong.title
            ?: holder.itemView.context.getString(R.string.unknown)
        holder.binding.artist.text = randomSong.artist
            ?: holder.itemView.context.getString(R.string.unknown)
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

            /**
             * Called when the user long-presses a song card.
             *
             * @param songs    The full list backing the carousel.
             * @param position The index of the long-pressed song within [songs].
             * @param imageView The album art [ImageView] used as a shared-element source for the menu.
             */
            fun onSongLongClicked(songs: MutableList<Audio>, position: Int, imageView: ImageView)
        }
    }
}

