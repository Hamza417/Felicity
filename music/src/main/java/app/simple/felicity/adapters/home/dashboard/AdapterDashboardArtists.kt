package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Artist

/**
 * Horizontal carousel adapter for [Artist] items on the dashboard.
 *
 * Shows the top artists ranked by how much you've been listening to them.
 * Each card shows the artist's art, name, and how many albums they have.
 *
 * @param artists The initial list of artists to show in the carousel.
 * @author Hamza417
 */
class AdapterDashboardArtists(
        private var artists: List<Artist>
) : RecyclerView.Adapter<AdapterDashboardArtists.Holder>() {

    private var callbacks: AdapterDashboardArtistsCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterCarouselBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = artists.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val artist = artists[position]

        // The Glide artist cover loader knows how to extract art from an Artist object.
        holder.binding.art.loadArtCoverWithPayload(artist)
        holder.binding.title.text = artist.name ?: holder.itemView.context.getString(
                app.simple.felicity.R.string.unknown)

        // Show album count as the subtitle — one number that tells a whole story.
        holder.binding.artist.text = holder.itemView.context.getString(
                app.simple.felicity.R.string.x_albums,
                artist.albumCount
        )

        holder.binding.container.setOnClickListener {
            callbacks?.onArtistClicked(artists[holder.bindingAdapterPosition])
        }

        holder.binding.container.setOnLongClickListener {
            callbacks?.onArtistLongClicked(
                    artists[holder.bindingAdapterPosition],
                    holder.binding.art as ImageView)
            true
        }
    }

    /**
     * Replaces the current data set with [newArtists] using [DiffUtil] for smooth animations.
     *
     * @param newArtists The updated list of artists to display.
     */
    fun updateData(newArtists: List<Artist>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = artists.size
            override fun getNewListSize() = newArtists.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                artists[oldItemPosition].id == newArtists[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                artists[oldItemPosition] == newArtists[newItemPosition]
        })
        artists = newArtists
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Attaches the callbacks used to respond to artist card interactions.
     *
     * @param callbacks The implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardArtistsCallbacks) {
        this.callbacks = callbacks
    }

    inner class Holder(val binding: AdapterCarouselBinding) :
            HorizontalListViewHolder(binding.root)

    companion object {
        /**
         * Callback interface for artist item interactions in the dashboard top artists carousel.
         */
        interface AdapterDashboardArtistsCallbacks {
            /**
             * Called when the user taps an artist card.
             *
             * @param artist The [Artist] that was tapped.
             */
            fun onArtistClicked(artist: Artist)

            /**
             * Called when the user long-presses an artist card.
             *
             * @param artist    The [Artist] that was long-pressed.
             * @param imageView The album art [ImageView] for shared-element transitions.
             */
            fun onArtistLongClicked(artist: Artist, imageView: ImageView)
        }
    }
}

