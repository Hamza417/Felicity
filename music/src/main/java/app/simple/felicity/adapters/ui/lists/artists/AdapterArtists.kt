package app.simple.felicity.adapters.ui.lists.artists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterArtistsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.artistcover.ArtistCoverUtils.loadArtistCover
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.Glide

class AdapterArtists(private val artists: List<Artist>) : RecyclerView.Adapter<AdapterArtists.Holder>() {
    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return artists[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterArtistsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val artist = artists[position]
        holder.binding.title.setTextOrUnknown(artist.name)
        holder.binding.count.setTextOrUnknown(holder.context.getString(R.string.x_songs, artist.trackCount))

        holder.binding.albumArt.loadArtistCover(artist)

        holder.binding.container.setOnLongClickListener {
            generalAdapterCallbacks?.onArtistLongClicked(artist, position, it)
            true
        }

        holder.binding.container.setOnClickListener {
            generalAdapterCallbacks?.onArtistClicked(artist, position, it)
        }
    }

    override fun getItemCount(): Int = artists.size

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    inner class Holder(val binding: AdapterArtistsBinding) : VerticalListViewHolder(binding.root)

}