package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Album

/**
 * Horizontal carousel adapter for [Album] items on the dashboard.
 *
 * Shows the top albums ranked by how much you've been listening to their tracks.
 * Each card shows the album art, album name, and the artist behind it.
 *
 * @param albums The initial list of albums to show in the carousel.
 * @author Hamza417
 */
class AdapterDashboardAlbums(
        private var albums: List<Album>
) : RecyclerView.Adapter<AdapterDashboardAlbums.Holder>() {

    private var callbacks: AdapterDashboardAlbumsCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterCarouselBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = albums.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = albums[position]

        // The Glide album cover loader handles art extraction from the Album model directly.
        holder.binding.art.loadArtCoverWithPayload(album)
        holder.binding.title.text = album.name ?: holder.itemView.context.getString(
                app.simple.felicity.R.string.unknown)
        holder.binding.artist.text = album.artist ?: holder.itemView.context.getString(
                app.simple.felicity.R.string.unknown)

        holder.binding.container.setOnClickListener {
            callbacks?.onAlbumClicked(albums[holder.bindingAdapterPosition])
        }

        holder.binding.container.setOnLongClickListener {
            callbacks?.onAlbumLongClicked(
                    albums[holder.bindingAdapterPosition],
                    holder.binding.art as ImageView)
            true
        }
    }

    /**
     * Replaces the current data set with [newAlbums] using [DiffUtil] for smooth animations.
     *
     * @param newAlbums The updated list of albums to display.
     */
    fun updateData(newAlbums: List<Album>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = albums.size
            override fun getNewListSize() = newAlbums.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                albums[oldItemPosition].id == newAlbums[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                albums[oldItemPosition] == newAlbums[newItemPosition]
        })
        albums = newAlbums
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Attaches the callbacks used to respond to album card interactions.
     *
     * @param callbacks The implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardAlbumsCallbacks) {
        this.callbacks = callbacks
    }

    inner class Holder(val binding: AdapterCarouselBinding) :
            HorizontalListViewHolder(binding.root)

    companion object {
        /**
         * Callback interface for album item interactions in the dashboard top albums carousel.
         */
        interface AdapterDashboardAlbumsCallbacks {
            /**
             * Called when the user taps an album card.
             *
             * @param album The [Album] that was tapped.
             */
            fun onAlbumClicked(album: Album)

            /**
             * Called when the user long-presses an album card.
             *
             * @param album     The [Album] that was long-pressed.
             * @param imageView The album art [ImageView] for shared-element transitions.
             */
            fun onAlbumLongClicked(album: Album, imageView: ImageView)
        }
    }
}

