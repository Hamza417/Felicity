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
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.models.PageItem
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.shared.constants.PageConstants
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.theme.managers.ThemeManager
import com.bumptech.glide.Glide

/**
 * Unified adapter for Album, Artist, and Genre pages
 * Handles different page types and adapts the header and sections accordingly
 */
class PageAdapter(
        private val data: PageData,
        private val pageType: PageType
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var listener: GeneralAdapterCallbacks? = null
    private val items = mutableListOf<PageItem>()

    /**
     * Sealed class to represent different page types
     */
    sealed class PageType {
        data class AlbumPage(val album: Album) : PageType()
        data class ArtistPage(val artist: Artist) : PageType()
        data class GenrePage(val genre: Genre) : PageType()
    }

    init {
        buildItemsList()
    }

    private fun buildItemsList() {
        items.clear()

        // Add header based on page type
        when (pageType) {
            is PageType.AlbumPage -> {
                items.add(PageItem.Header(
                        album = pageType.album,
                        totalSongs = data.songs.size,
                        totalDuration = data.songs.sumOf { it.duration },
                        albumArtists = data.artists,
                        songs = data.songs
                ))
            }
            is PageType.ArtistPage -> {
                items.add(PageItem.Header(
                        album = Album(
                                id = pageType.artist.id,
                                name = pageType.artist.name,
                                artist = pageType.artist.name,
                                artistId = pageType.artist.id
                        ),
                        totalSongs = data.songs.size,
                        totalDuration = data.songs.sumOf { it.duration },
                        albumArtists = data.artists,
                        songs = data.songs
                ))
            }
            is PageType.GenrePage -> {
                items.add(PageItem.Header(
                        album = Album(
                                id = pageType.genre.id,
                                name = pageType.genre.name,
                                artist = "",
                                artistId = 0L
                        ),
                        totalSongs = data.songs.size,
                        totalDuration = data.songs.sumOf { it.duration },
                        albumArtists = data.artists,
                        songs = data.songs
                ))
            }
        }

        // Add all songs
        data.songs.forEachIndexed { index, audio ->
            items.add(PageItem.SongItem(
                    audio = audio,
                    position = index,
                    allSongs = data.songs
            ))
        }

        // Add albums section if available
        if (data.albums.isNotEmpty()) {
            val artistName = when (pageType) {
                is PageType.AlbumPage -> pageType.album.name
                is PageType.ArtistPage -> pageType.artist.name
                is PageType.GenrePage -> pageType.genre.name
            }
            items.add(PageItem.AlbumsSection(
                    albums = data.albums,
                    artistName = artistName
            ))
        }

        // Add artists section if available
        if (data.artists.isNotEmpty()) {
            items.add(PageItem.ArtistsSection(
                    artists = data.artists
            ))
        }

        // Add genres section if available
        if (data.genres.isNotEmpty()) {
            items.add(PageItem.GenresSection(
                    genres = data.genres
            ))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            PageConstants.VIEW_TYPE_HEADER -> {
                when (pageType) {
                    is PageType.GenrePage -> {
                        GenreHeader(AdapterHeaderArtistPageBinding.inflate(inflater, parent, false))
                    }
                    else -> {
                        Header(AdapterHeaderArtistPageBinding.inflate(inflater, parent, false))
                    }
                }
            }
            PageConstants.VIEW_TYPE_ALBUMS -> {
                Albums(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            PageConstants.VIEW_TYPE_ARTISTS -> {
                Artists(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            PageConstants.VIEW_TYPE_GENRES -> {
                Genres(AdapterGenreAlbumsBinding.inflate(inflater, parent, false))
            }
            PageConstants.VIEW_TYPE_SONG -> {
                Songs(AdapterStyleListBinding.inflate(inflater, parent, false))
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is Header -> {
                val headerItem = item as PageItem.Header
                holder.bind(headerItem, data, pageType)
            }
            is GenreHeader -> {
                val headerItem = item as PageItem.Header
                holder.bind(headerItem, data, pageType)
            }
            is Albums -> {
                val albumsItem = item as PageItem.AlbumsSection
                holder.bind(albumsItem, listener, pageType)
            }
            is Artists -> {
                val artistsItem = item as PageItem.ArtistsSection
                holder.bind(artistsItem, listener, pageType)
            }
            is Genres -> {
                val genresItem = item as PageItem.GenresSection
                holder.bind(genresItem, listener)
            }
            is Songs -> {
                val songItem = item as PageItem.SongItem
                holder.bind(songItem.audio)
                holder.binding.container.setOnClickListener {
                    listener?.onSongClicked(songItem.allSongs, songItem.position, holder.binding.cover)
                }

                holder.binding.container.setOnLongClickListener {
                    listener?.onSongLongClicked(songItem.allSongs, songItem.position, holder.binding.cover)
                    true
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
            is Genres -> {
                holder.cleanup()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PageItem.Header -> PageConstants.VIEW_TYPE_HEADER
            is PageItem.AlbumsSection -> PageConstants.VIEW_TYPE_ALBUMS
            is PageItem.ArtistsSection -> PageConstants.VIEW_TYPE_ARTISTS
            is PageItem.GenresSection -> PageConstants.VIEW_TYPE_GENRES
            is PageItem.SongItem -> PageConstants.VIEW_TYPE_SONG
        }
    }

    inner class Albums(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        private var adapter: AdapterCarouselItems? = null

        fun bind(item: PageItem.AlbumsSection, adapterListener: GeneralAdapterCallbacks?, pageType: PageType) {
            if (item.albums.isEmpty()) {
                binding.root.visibility = View.GONE
                return
            }

            binding.root.visibility = View.VISIBLE

            // Set title based on page type
            binding.title.text = when (pageType) {
                is PageType.AlbumPage -> context.getString(
                        R.string.albums_from_artist,
                        item.artistName ?: context.getString(R.string.unknown)
                )
                is PageType.ArtistPage -> context.getString(
                        R.string.albums_from_artist,
                        item.artistName ?: context.getString(R.string.unknown)
                )
                is PageType.GenrePage -> context.getString(
                        R.string.albums_in_genre,
                        item.artistName ?: context.getString(R.string.unknown)
                )
            }

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

        fun bind(item: PageItem.ArtistsSection, adapterListener: GeneralAdapterCallbacks?, pageType: PageType) {
            if (item.artists.isEmpty()) {
                binding.root.visibility = View.GONE
                return
            }

            binding.root.visibility = View.VISIBLE

            // Set title based on page type
            binding.title.text = when (pageType) {
                is PageType.AlbumPage -> context.getString(R.string.album_artists)
                is PageType.ArtistPage -> context.getString(
                        R.string.with_other_artists,
                        pageType.artist.name ?: context.getString(R.string.unknown)
                )
                is PageType.GenrePage -> context.getString(
                        R.string.artists_in_genre,
                        pageType.genre.name ?: context.getString(R.string.unknown)
                )
            }

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

    inner class Genres(val binding: AdapterGenreAlbumsBinding) : VerticalListViewHolder(binding.root) {
        private var adapter: AdapterCarouselItems? = null

        fun bind(item: PageItem.GenresSection, adapterListener: GeneralAdapterCallbacks?) {
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
        fun bind(item: PageItem.Header, pageData: PageData, pageType: PageType) {
            binding.apply {
                name.text = item.album.name ?: context.getString(R.string.unknown)
                songs.text = item.totalSongs.toString()
                albums.text = pageData.albums.size.toString()
                artists.text = item.albumArtists.size.toString()
                totalTime.text = item.totalDuration.toHighlightedTimeString(ThemeManager.accent.primaryAccentColor)

                // Show art flow for albums and artists
                when (pageType) {
                    is PageType.AlbumPage, is PageType.ArtistPage -> {
                        when {
                            item.songs.isNotEmpty() -> {
                                artFlow.setSliderAdapter(ArtistArtFlowAdapter(ArtFlowData(R.string.songs, item.songs)))
                            }
                            pageData.albums.isNotEmpty() -> {
                                artFlow.setSliderAdapter(ArtistArtFlowAdapter(ArtFlowData(R.string.albums, pageData.albums)))
                            }
                            pageData.artists.isNotEmpty() -> {
                                artFlow.setSliderAdapter(ArtistArtFlowAdapter(ArtFlowData(R.string.artists, pageData.artists)))
                            }
                        }
                    }
                    is PageType.GenrePage -> {
                        artFlow.visible(false)
                    }
                }

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

    // TODO - the header can be merged too but for now keeping it until I am sure of the interface
    inner class GenreHeader(val binding: AdapterHeaderArtistPageBinding) : VerticalListViewHolder(binding.root) {
        fun bind(item: PageItem.Header, pageData: PageData, pageType: PageType) {
            binding.apply {
                val genrePage = pageType as PageType.GenrePage
                name.text = genrePage.genre.name ?: context.getString(R.string.unknown)
                songs.text = item.totalSongs.toString()
                artists.text = pageData.artists.size.toString()
                albums.text = pageData.albums.size.toString()
                totalTime.text = item.totalDuration.toHighlightedTimeString(ThemeManager.accent.primaryAccentColor)
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
}