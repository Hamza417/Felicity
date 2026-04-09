package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterSearchSectionHeaderBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleLabelsBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.models.SearchResults
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

/**
 * A unified search results adapter that replaces the previous multi-[RecyclerView.Adapter] /
 * [androidx.recyclerview.widget.ConcatAdapter] approach. Songs, albums, artists, genres, and
 * their section headers are all rendered as distinct view types within this single adapter,
 * eliminating the position out-of-bounds crashes that are inherent to
 * [androidx.recyclerview.widget.ConcatAdapter] + [androidx.recyclerview.widget.GridLayoutManager]
 * combinations.
 *
 * Call [submitResults] to push new search results; [AsyncListDiffer] handles efficient
 * diffing so only the changed rows are animated.
 *
 * @author Hamza417
 */
class AdapterSearch : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    /** Backing song list used as the parameter for song-click callbacks. */
    private var songs: MutableList<Audio> = mutableListOf()

    /** Backing album list used as the parameter for album-click callbacks. */
    private var albums: MutableList<Album> = mutableListOf()

    /** Backing artist list used as the parameter for artist-click callbacks. */
    private var artists: MutableList<Artist> = mutableListOf()

    /** Backing genre list kept for consistency; genres are passed individually to callbacks. */
    private var genres: MutableList<Genre> = mutableListOf()

    /** Current layout mode that determines which song view type is inflated. */
    var layoutMode: CommonPreferencesConstants.LayoutMode = SearchPreferences.getGridSize()

    private val diffCallback = object : DiffUtil.ItemCallback<SearchAdapterItem>() {
        override fun areItemsTheSame(
                oldItem: SearchAdapterItem,
                newItem: SearchAdapterItem,
        ): Boolean {
            if (oldItem::class != newItem::class) return false
            return when {
                oldItem is SearchAdapterItem.Header && newItem is SearchAdapterItem.Header ->
                    oldItem.title == newItem.title

                oldItem is SearchAdapterItem.SongItem && newItem is SearchAdapterItem.SongItem ->
                    oldItem.audio.id == newItem.audio.id

                oldItem is SearchAdapterItem.AlbumItem && newItem is SearchAdapterItem.AlbumItem ->
                    oldItem.album.id == newItem.album.id

                oldItem is SearchAdapterItem.ArtistItem && newItem is SearchAdapterItem.ArtistItem ->
                    oldItem.artist.id == newItem.artist.id

                oldItem is SearchAdapterItem.GenreItem && newItem is SearchAdapterItem.GenreItem ->
                    oldItem.genre.id == newItem.genre.id

                else -> false
            }
        }

        override fun areContentsTheSame(
                oldItem: SearchAdapterItem,
                newItem: SearchAdapterItem,
        ): Boolean = oldItem == newItem
    }

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val items: List<SearchAdapterItem>
        get() = differ.currentList

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchAdapterItem.Header -> VIEW_TYPE_HEADER
            is SearchAdapterItem.AlbumItem -> VIEW_TYPE_ALBUM
            is SearchAdapterItem.ArtistItem -> VIEW_TYPE_ARTIST
            is SearchAdapterItem.GenreItem -> VIEW_TYPE_GENRE
            is SearchAdapterItem.SongItem -> when {
                layoutMode.isLabel -> VIEW_TYPE_SONG_LABEL
                layoutMode.isGrid -> VIEW_TYPE_SONG_GRID
                else -> VIEW_TYPE_SONG_LIST
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER ->
                HeaderHolder(AdapterSearchSectionHeaderBinding.inflate(inflater, parent, false))

            VIEW_TYPE_SONG_GRID ->
                SongGridHolder(AdapterStyleGridBinding.inflate(inflater, parent, false))

            VIEW_TYPE_SONG_LABEL ->
                SongLabelHolder(AdapterStyleLabelsBinding.inflate(inflater, parent, false))

            VIEW_TYPE_ALBUM ->
                AlbumHolder(AdapterStyleListBinding.inflate(inflater, parent, false))

            VIEW_TYPE_ARTIST ->
                ArtistHolder(AdapterStyleListBinding.inflate(inflater, parent, false))

            VIEW_TYPE_GENRE ->
                GenreHolder(AdapterStyleListBinding.inflate(inflater, parent, false))

            else ->
                SongListHolder(AdapterStyleListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        when (holder) {
            is HeaderHolder -> holder.bind(items[position] as SearchAdapterItem.Header)
            is SongListHolder -> holder.bind(items[position] as SearchAdapterItem.SongItem, isLightBind)
            is SongGridHolder -> holder.bind(items[position] as SearchAdapterItem.SongItem, isLightBind)
            is SongLabelHolder -> holder.bind(items[position] as SearchAdapterItem.SongItem, isLightBind)
            is AlbumHolder -> holder.bind(items[position] as SearchAdapterItem.AlbumItem, isLightBind)
            is ArtistHolder -> holder.bind(items[position] as SearchAdapterItem.ArtistItem, isLightBind)
            is GenreHolder -> holder.bind(items[position] as SearchAdapterItem.GenreItem)
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is SongListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is SongGridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            is AlbumHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is ArtistHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GenreHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            else -> Unit
        }
    }

    /**
     * Builds a flat item list from [results] — with section headers for non-empty categories —
     * then submits it to [AsyncListDiffer] for efficient, animated diffing.
     *
     * @param results The latest [SearchResults] from the search view-model.
     * @param songsHeader Label used for the songs section header.
     * @param albumsHeader Label used for the albums section header.
     * @param artistsHeader Label used for the artists section header.
     * @param genresHeader Label used for the genres section header.
     */
    fun submitResults(
            results: SearchResults,
            songsHeader: String,
            albumsHeader: String,
            artistsHeader: String,
            genresHeader: String,
    ) {
        songs = results.songs.toMutableList()
        albums = results.albums.toMutableList()
        artists = results.artists.toMutableList()
        genres = results.genres.toMutableList()

        val newItems = buildList {
            if (songs.isNotEmpty()) {
                add(SearchAdapterItem.Header(songsHeader))
                songs.forEach { add(SearchAdapterItem.SongItem(it)) }
            }
            if (albums.isNotEmpty()) {
                add(SearchAdapterItem.Header(albumsHeader))
                albums.forEach { add(SearchAdapterItem.AlbumItem(it)) }
            }
            if (artists.isNotEmpty()) {
                add(SearchAdapterItem.Header(artistsHeader))
                artists.forEach { add(SearchAdapterItem.ArtistItem(it)) }
            }
            if (genres.isNotEmpty()) {
                add(SearchAdapterItem.Header(genresHeader))
                genres.forEach { add(SearchAdapterItem.GenreItem(it)) }
            }
        }

        differ.submitList(newItems)
    }

    /**
     * Sets the general adapter callbacks used for item click and long-click handling.
     *
     * @param callbacks The [GeneralAdapterCallbacks] implementation to receive events.
     */
    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /**
     * ViewHolder for labeled section separator rows (Songs, Albums, Artists, Genres).
     *
     * @param binding The view binding for the section header layout.
     */
    inner class HeaderHolder(val binding: AdapterSearchSectionHeaderBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.Header) {
            binding.sectionTitle.text = item.title
        }
    }

    /**
     * ViewHolder for song items rendered in list mode.
     *
     * @param binding The view binding for the list item layout.
     */
    inner class SongListHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.SongItem, isLightBind: Boolean) {
            val audio = item.audio
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.getArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            binding.container.setAudioID(audio.id)

            if (isLightBind) return

            binding.cover.loadArtCoverWithPayload(audio)
            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongLongClicked(songs, songs.indexOf(audio), binding.cover)
                }
                true
            }
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongClicked(songs, songs.indexOf(audio), it)
                }
            }
        }
    }

    /**
     * ViewHolder for song items rendered in grid mode.
     *
     * @param binding The view binding for the grid item layout.
     */
    inner class SongGridHolder(val binding: AdapterStyleGridBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.SongItem, isLightBind: Boolean) {
            val audio = item.audio
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.container.setAudioID(audio.id)

            if (isLightBind) return

            binding.albumArt.loadArtCoverWithPayload(audio)
            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongLongClicked(songs, songs.indexOf(audio), null)
                }
                true
            }
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongClicked(songs, songs.indexOf(audio), it)
                }
            }
        }
    }

    /**
     * ViewHolder for song items rendered in label mode.
     *
     * @param binding The view binding for the label item layout.
     */
    inner class SongLabelHolder(val binding: AdapterStyleLabelsBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.SongItem, isLightBind: Boolean) {
            val audio = item.audio
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.getArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            binding.container.setAudioID(audio.id)

            if (isLightBind) return

            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongLongClicked(songs, songs.indexOf(audio), null)
                }
                true
            }
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onSongClicked(songs, songs.indexOf(audio), it)
                }
            }
        }
    }

    /**
     * ViewHolder for album result rows.
     *
     * @param binding The view binding for the list item layout.
     */
    inner class AlbumHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.AlbumItem, isLightBind: Boolean) {
            val album = item.album
            binding.title.setTextOrUnknown(album.name)
            binding.secondaryDetail.setTextOrUnknown(album.artist)
            binding.tertiaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount)
            )

            if (isLightBind) return

            binding.cover.loadArtCoverWithPayload(album)
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onAlbumClicked(albums, albums.indexOf(album), it)
                }
            }
            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, albums.indexOf(album), it)
                }
                true
            }
        }
    }

    /**
     * ViewHolder for artist result rows.
     *
     * @param binding The view binding for the list item layout.
     */
    inner class ArtistHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.ArtistItem, isLightBind: Boolean) {
            val artist = item.artist
            binding.title.setTextOrUnknown(artist.name)
            binding.secondaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, artist.trackCount, artist.trackCount)
            )
            binding.tertiaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_albums, artist.albumCount, artist.albumCount)
            )

            if (isLightBind) return

            binding.cover.loadArtCoverWithPayload(item = artist)
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onArtistClicked(artists, artists.indexOf(artist), it)
                }
            }
            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onArtistLongClicked(artists, artists.indexOf(artist), it)
                }
                true
            }
        }
    }

    /**
     * ViewHolder for genre result rows.
     *
     * @param binding The view binding for the list item layout.
     */
    inner class GenreHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.GenreItem) {
            val genre = item.genre
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.secondaryDetail.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, genre.songCount, genre.songCount
            )
            binding.tertiaryDetail.gone(false)
            binding.cover.loadArtCoverWithPayload(genre)
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onGenreClicked(genre, it)
            }
        }
    }

    companion object {
        /** View type for section header rows (Songs, Albums, Artists, Genres). */
        const val VIEW_TYPE_HEADER = 0

        /** View type for song items in list mode. */
        const val VIEW_TYPE_SONG_LIST = 1

        /** View type for song items in grid mode. */
        const val VIEW_TYPE_SONG_GRID = 2

        /** View type for song items in label mode. */
        const val VIEW_TYPE_SONG_LABEL = 3

        /** View type for album result rows. */
        const val VIEW_TYPE_ALBUM = 4

        /** View type for artist result rows. */
        const val VIEW_TYPE_ARTIST = 5

        /** View type for genre result rows. */
        const val VIEW_TYPE_GENRE = 6
    }
}

/**
 * Represents a single entry in the flattened search results list consumed by [AdapterSearch].
 * Each subtype maps to a distinct view type and [RecyclerView.ViewHolder].
 */
sealed class SearchAdapterItem {

    /**
     * A labeled section separator row shown above each result category.
     *
     * @param title The human-readable category label displayed in the header row.
     */
    data class Header(val title: String) : SearchAdapterItem()

    /**
     * A song result row.
     *
     * @param audio The [Audio] data to display.
     */
    data class SongItem(val audio: Audio) : SearchAdapterItem()

    /**
     * An album result row.
     *
     * @param album The [Album] data to display.
     */
    data class AlbumItem(val album: Album) : SearchAdapterItem()

    /**
     * An artist result row.
     *
     * @param artist The [Artist] data to display.
     */
    data class ArtistItem(val artist: Artist) : SearchAdapterItem()

    /**
     * A genre result row.
     *
     * @param genre The [Genre] data to display.
     */
    data class GenreItem(val genre: Genre) : SearchAdapterItem()
}
