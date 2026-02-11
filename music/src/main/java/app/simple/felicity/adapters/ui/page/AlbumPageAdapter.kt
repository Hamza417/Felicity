package app.simple.felicity.adapters.ui.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.home.sub.AdapterCarouselItems
import app.simple.felicity.adapters.home.sub.ArtistArtFlowAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterGenreAlbumsBinding
import app.simple.felicity.databinding.AdapterHeaderArtistPageBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.models.AlbumPageItem
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.theme.managers.ThemeManager
import com.bumptech.glide.Glide

class AlbumPageAdapter(private val data: PageData, private val album: Album) :
        RecyclerView.Adapter<VerticalListViewHolder>() {

    private var listener: GeneralAdapterCallbacks? = null
    private val items = mutableListOf<AlbumPageItem>()

    init {
        buildItemsList()
    }

    private fun buildItemsList() {
        items.clear()

        // Add header
        items.add(AlbumPageItem.Header(
                album = album,
                totalSongs = data.songs.size,
                totalDuration = data.songs.sumOf { it.duration },
                albumArtists = data.artists,
                songs = data.songs
        ))

        // Add all songs
        data.songs.forEachIndexed { index, audio ->
            items.add(AlbumPageItem.SongItem(
                    audio = audio,
                    position = index,
                    allSongs = data.songs
            ))
        }

        // Add albums section if available
        if (data.albums.isNotEmpty()) {
            items.add(AlbumPageItem.AlbumsSection(
                    albums = data.albums,
                    artistName = album.name
            ))
        }

        // Add artists section if available
        if (data.artists.isNotEmpty()) {
            items.add(AlbumPageItem.ArtistsSection(
                    artists = data.artists
            ))
        }

        // Add genres section if available
        if (data.genres.isNotEmpty()) {
            items.add(AlbumPageItem.GenresSection(
                    genres = data.genres
            ))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                Header(AdapterHeaderArtistPageBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_ALBUMS -> {
                Albums(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_ARTISTS -> {
                Artists(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_GENRES -> {
                Genre(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            VIEW_TYPE_SONG -> {
                Songs(AdapterStyleListBinding.inflate(inflater, parent, false))
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is Header -> {
                val headerItem = item as AlbumPageItem.Header
                holder.bind(headerItem, data)
            }
            is Albums -> {
                val albumsItem = item as AlbumPageItem.AlbumsSection
                holder.bind(albumsItem, listener)
            }
            is Artists -> {
                val artistsItem = item as AlbumPageItem.ArtistsSection
                holder.bind(artistsItem, listener)
            }
            is Genre -> {
                val genresItem = item as AlbumPageItem.GenresSection
                holder.bind(genresItem, listener)
            }
            is Songs -> {
                val songItem = item as AlbumPageItem.SongItem
                holder.bind(songItem.audio)
                holder.binding.container.setOnClickListener {
                    listener?.onSongClicked(songItem.allSongs, songItem.position, holder.binding.cover)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is Songs -> {
                Glide.with(holder.binding.cover).clear(holder.binding.cover)
            }
            is Albums -> {
                holder.cleanup()
            }
            is Artists -> {
                holder.cleanup()
            }
            is Genre -> {
                holder.cleanup()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumPageItem.Header -> VIEW_TYPE_HEADER
            is AlbumPageItem.AlbumsSection -> VIEW_TYPE_ALBUMS
            is AlbumPageItem.ArtistsSection -> VIEW_TYPE_ARTISTS
            is AlbumPageItem.GenresSection -> VIEW_TYPE_GENRES
            is AlbumPageItem.SongItem -> VIEW_TYPE_SONG
        }
    }

    inner class Albums(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        private var adapter: AdapterCarouselItems? = null

        fun bind(item: AlbumPageItem.AlbumsSection, adapterListener: GeneralAdapterCallbacks?) {
            if (item.albums.isEmpty()) {
                binding.root.visibility = View.GONE
                return
            }

            binding.root.visibility = View.VISIBLE
            binding.title.text = context.getString(
                    R.string.albums_from_artist,
                    item.artistName ?: context.getString(R.string.unknown)
            )

            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.setHasFixedSize(true)
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
            }

            adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, item.albums))
            binding.recyclerView.adapter = adapter

            adapter?.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                override fun onClicked(view: View, position: Int) {
                    adapterListener?.onAlbumClicked(item.albums, position, view)
                }
            })
        }

        fun cleanup() {
            adapter = null
        }
    }

    inner class Artists(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        private var adapter: AdapterCarouselItems? = null

        fun bind(item: AlbumPageItem.ArtistsSection, adapterListener: GeneralAdapterCallbacks?) {
            if (item.artists.isEmpty()) {
                binding.root.visibility = View.GONE
                return
            }

            binding.root.visibility = View.VISIBLE
            binding.title.text = context.getString(R.string.album_artists)

            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.setHasFixedSize(true)
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
            }

            adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, item.artists))
            binding.recyclerView.adapter = adapter

            adapter?.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                override fun onClicked(view: View, position: Int) {
                    adapterListener?.onArtistClicked(item.artists, position, view)
                }
            })
        }

        fun cleanup() {
            adapter = null
        }
    }

    inner class Genre(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        private var adapter: AdapterCarouselItems? = null

        fun bind(item: AlbumPageItem.GenresSection, adapterListener: GeneralAdapterCallbacks?) {
            if (item.genres.isEmpty()) {
                binding.root.visibility = View.GONE
                return
            }

            binding.root.visibility = View.VISIBLE
            binding.title.text = context.getString(R.string.album_genres)

            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.setHasFixedSize(true)
                binding.recyclerView.addItemDecoration(LinearHorizontalSpacingDecoration(24))
            }

            adapter = AdapterCarouselItems(ArtFlowData(R.string.unknown, item.genres))
            binding.recyclerView.adapter = adapter

            adapter?.setAdapterCarouselCallbacks(object : AdapterCarouselItems.Companion.AdapterCarouselCallbacks {
                override fun onClicked(view: View, position: Int) {
                    adapterListener?.onGenreClicked(item.genres[position], view)
                }
            })
        }

        fun cleanup() {
            adapter = null
        }
    }

    inner class Header(val binding: AdapterHeaderArtistPageBinding) : VerticalListViewHolder(binding.root) {
        fun bind(item: AlbumPageItem.Header, pageData: PageData) {
            binding.apply {
                name.text = item.album.name ?: context.getString(R.string.unknown)
                songs.text = item.totalSongs.toString()
                albums.text = pageData.albums.size.toString()
                artists.text = item.albumArtists.size.toString()
                totalTime.text = item.totalDuration.toHighlightedTimeString(ThemeManager.accent.primaryAccentColor)

                artFlow.visible(true)
                artFlow.setSliderAdapter(ArtistArtFlowAdapter(ArtFlowData(R.string.songs, item.songs)))

                play.setOnClickListener {
                    listener?.onPlayClicked(item.songs, 0)
                }
                shuffle.setOnClickListener {
                    listener?.onShuffleClicked(item.songs, 0)
                }
                menu.setOnClickListener {
                    listener?.onMenuClicked(it)
                }
            }
        }
    }

    fun setArtistAdapterListener(listener: GeneralAdapterCallbacks) {
        this.listener = listener
    }

    companion object {
        // View types based on sealed class types
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ALBUMS = 1
        private const val VIEW_TYPE_ARTISTS = 2
        private const val VIEW_TYPE_GENRES = 3
        private const val VIEW_TYPE_SONG = 4
    }
}