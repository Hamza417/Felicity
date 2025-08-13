package app.simple.felicity.adapters.ui.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.home.sub.AdapterCarouselItems
import app.simple.felicity.adapters.home.sub.ArtistArtFlowAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.core.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.core.utils.ViewUtils.visible
import app.simple.felicity.databinding.AdapterGenreAlbumsBinding
import app.simple.felicity.databinding.AdapterHeaderArtistPageBinding
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.uricover.UriCoverUtils.loadFromUri
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Song
import app.simple.felicity.theme.managers.ThemeManager
import com.bumptech.glide.Glide

class ArtistDetailsAdapter(private val data: CollectionPageData, private val artist: Artist) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var listener: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterHeaderArtistPageBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ALBUMS -> {
                Albums(AdapterGenreAlbumsBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ARTISTS -> {
                Artists(AdapterGenreAlbumsBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                Songs(AdapterSongsBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Header -> {
                holder.binding.apply {
                    name.text = artist.name ?: holder.context.getString(R.string.unknown)
                    songs.text = data.songs.size.toString()
                    albums.text = data.albums.size.toString()
                    artists.text = data.artists.size.toString()
                    totalTime.text = data.songs.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.accent.primaryAccentColor)
                    // poster.loadFromDescriptor(data.songs.first().uri, roundedCorners = false, blur = false, skipCache = true)

                    artFlow.visible(false)
                    artFlow.setSliderAdapter(ArtistArtFlowAdapter(ArtFlowData(R.string.songs, data.songs)))
                }
            }
            is Albums -> {
                /* no-op */
            }
            is Songs -> {
                holder.bind(data.songs[position - 1]) // Adjust for header

                holder.binding.container.setOnClickListener {
                    listener?.onSongClicked(data.songs, position - SONGS_POSITION, holder.binding.albumArt)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return data.songs.size.plus(EXTRA_ROWS)
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        if (holder is Songs) {
            Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> {
                RecyclerViewUtils.TYPE_HEADER
            }
            data.songs.size.plus(1) -> {
                RecyclerViewUtils.TYPE_ALBUMS
            }
            data.songs.size.plus(2) -> {
                RecyclerViewUtils.TYPE_ARTISTS
            }
            else -> {
                RecyclerViewUtils.TYPE_ITEM
            }
        }
    }

    inner class Songs(val binding: AdapterSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                title.setTextOrUnknown(song.title)
                artists.setTextOrUnknown(song.artist)
                album.setTextOrUnknown(song.album)

                albumArt.loadFromUri(song.artworkUri!!)
                albumArt.transitionName = song.path
            }
        }
    }

    inner class Albums(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        init {
            if (data.albums.isNotEmpty()) {
                binding.recyclerView.setHasFixedSize(true)
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
                val adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, data.albums))
                binding.title.text = binding.title.context.getString(R.string.albums_from_artist, artist.name ?: context.getString(R.string.unknown))
                binding.recyclerView.adapter = adapter
            } else {
                binding.title.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    inner class Artists(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        init {
            if (data.artists.isNotEmpty()) {
                binding.recyclerView.setHasFixedSize(true)
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
                val adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, data.artists))
                binding.title.text = binding.title.context.getString(R.string.with_other_artists, artist.name ?: context.getString(R.string.unknown))
                binding.recyclerView.adapter = adapter

                adapter.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                    override fun onClicked(view: View, position: Int) {
                        listener?.onArtistClicked(data.artists[position])
                    }
                })
            } else {
                binding.title.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    inner class Header(val binding: AdapterHeaderArtistPageBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.play.setOnClickListener {
                listener?.onPlayClicked(data.songs, bindingAdapterPosition)
            }
            binding.shuffle.setOnClickListener {
                listener?.onShuffleClicked(data.songs, bindingAdapterPosition)
            }
            binding.menu.setOnClickListener {
                listener?.onMenuClicked(it)
            }
        }
    }

    fun setArtistAdapterListener(listener: GeneralAdapterCallbacks) {
        this.listener = listener
    }

    companion object {
        private const val TAG = "GenreSongsAdapter"
        private const val EXTRA_ROWS = 3 // Header, Albums, Artists
        private const val SONGS_POSITION = 1 // Position of songs in the adapter
    }
}