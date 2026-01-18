package app.simple.felicity.adapters.ui.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.home.sub.AdapterCarouselItems
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterGenreAlbumsBinding
import app.simple.felicity.databinding.AdapterHeaderGenrePageBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import com.bumptech.glide.Glide

class GenrePageAdapter(private val data: CollectionPageData, private val genre: Genre) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterHeaderGenrePageBinding.inflate(
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
                Songs(AdapterStyleListBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Header -> {
                holder.binding.apply {
                    name.text = genre.name ?: holder.context.getString(R.string.unknown)
                    songs.text = data.songs.size.toString()
                    artists.text = data.artists.size.toString()
                    albums.text = data.albums.size.toString()
                    totalTime.text = data.songs.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.accent.primaryAccentColor)
                    poster.loadArtCoverWithPayload(genre)

                    menu.setOnClickListener {
                        generalAdapterCallbacks?.onMenuClicked(it)
                    }
                }
            }
            is Albums -> {

            }
            is Songs -> {
                holder.bind(data.songs[position - 1]) // Adjust for header

                holder.binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onSongClicked(data.songs, position - SONGS_POSITION, holder.binding.cover)
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
            Glide.with(holder.binding.cover).clear(holder.binding.cover)
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

    inner class Albums(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        init {
            if (data.albums.isNotEmpty()) {
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
                val adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, data.albums))
                binding.title.text = binding.title.context.getString(R.string.albums_in_genre, genre.name ?: context.getString(R.string.unknown))
                binding.recyclerView.adapter = adapter

                adapter.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                    override fun onClicked(view: View, position: Int) {
                        generalAdapterCallbacks?.onAlbumClicked(data.albums, position, view)
                    }
                })
            } else {
                binding.title.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    inner class Artists(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        init {
            if (data.artists.isNotEmpty()) {
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
                val adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, data.artists))
                binding.title.text = binding.title.context.getString(R.string.artists_in_genre, genre.name ?: context.getString(R.string.unknown))
                binding.recyclerView.adapter = adapter

                adapter.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                    override fun onClicked(view: View, position: Int) {
                        generalAdapterCallbacks?.onArtistClicked(data.artists, position, view)
                    }
                })
            } else {
                binding.title.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    inner class Header(val binding: AdapterHeaderGenrePageBinding) : VerticalListViewHolder(binding.root) {
        init {
            binding.play.setOnClickListener {
                generalAdapterCallbacks?.onPlayClicked(data.songs, bindingAdapterPosition)
            }
            binding.shuffle.setOnClickListener {
                generalAdapterCallbacks?.onShuffleClicked(data.songs, bindingAdapterPosition)
            }
        }
    }

    fun setCallbacks(generalAdapterCallbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = generalAdapterCallbacks
    }

    companion object {
        private const val TAG = "GenreSongsAdapter"
        private const val EXTRA_ROWS = 3 // Header, Albums, Artists
        private const val SONGS_POSITION = 1 // Position of songs in the adapter
    }
}