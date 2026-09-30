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
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.repository.utils.AudioUtils.getProperAlbum
import app.simple.felicity.repository.utils.AudioUtils.getProperArtists
import app.simple.felicity.repository.utils.AudioUtils.getProperTitle
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

    /** Backing composer list used as the parameter for composer-click callbacks. */
    private var composers: MutableList<Artist> = mutableListOf()

    /** Backing year-group list kept for consistency; years are passed individually to callbacks. */
    private var years: MutableList<YearGroup> = mutableListOf()

    /** Current layout mode that determines which song view type is inflated. */
    var layoutMode: CommonPreferencesConstants.LayoutMode = SearchPreferences.getGridSize()

    private val diffCallback = object : DiffUtil.ItemCallback<SearchAdapterItem>() {
        override fun areItemsTheSame(
                oldItem: SearchAdapterItem,
                newItem: SearchAdapterItem,
        ): Boolean {
            if (oldItem::class != newItem::class) return false
            @Suppress("IntroduceWhenSubject")
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

                oldItem is SearchAdapterItem.ComposerItem && newItem is SearchAdapterItem.ComposerItem ->
                    oldItem.composer.id == newItem.composer.id

                oldItem is SearchAdapterItem.YearItem && newItem is SearchAdapterItem.YearItem ->
                    oldItem.yearGroup.id == newItem.yearGroup.id

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
            is SearchAdapterItem.ComposerItem -> VIEW_TYPE_COMPOSER
            is SearchAdapterItem.YearItem -> VIEW_TYPE_YEAR
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

            VIEW_TYPE_COMPOSER ->
                ComposerHolder(AdapterStyleListBinding.inflate(inflater, parent, false))

            VIEW_TYPE_YEAR ->
                YearHolder(AdapterStyleListBinding.inflate(inflater, parent, false))

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
            is ComposerHolder -> holder.bind(items[position] as SearchAdapterItem.ComposerItem)
            is YearHolder -> holder.bind(items[position] as SearchAdapterItem.YearItem)
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
            is ComposerHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is YearHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
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
     * @param composersHeader Label used for the composers section header.
     * @param yearsHeader Label used for the years section header.
     */
    fun submitResults(
            results: SearchResults,
            songsHeader: String,
            albumsHeader: String,
            artistsHeader: String,
            genresHeader: String,
            composersHeader: String,
            yearsHeader: String,
    ) {
        songs = results.songs.toMutableList()
        albums = results.albums.toMutableList()
        artists = results.artists.toMutableList()
        genres = results.genres.toMutableList()
        composers = results.composers.toMutableList()
        years = results.years.toMutableList()

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
            if (composers.isNotEmpty()) {
                add(SearchAdapterItem.Header(composersHeader))
                composers.forEach { add(SearchAdapterItem.ComposerItem(it)) }
            }
            if (years.isNotEmpty()) {
                add(SearchAdapterItem.Header(yearsHeader))
                years.forEach { add(SearchAdapterItem.YearItem(it)) }
            }
        }

        differ.submitList(newItems)
    }

    /**
     * Returns the adapter position of the section header whose title matches [headerTitle],
     * or -1 if no such header exists in the current list (e.g., the section is empty).
     *
     * @param headerTitle The exact label string used for the section header.
     * @return Zero-based adapter position, or -1 when not found.
     */
    fun getSectionPosition(headerTitle: String): Int {
        return items.indexOfFirst { it is SearchAdapterItem.Header && it.title == headerTitle }
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
            binding.title.setTextOrUnknown(audio.getProperTitle())
            binding.secondaryDetail.setTextOrUnknown(audio.getProperArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.getProperAlbum())
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
            binding.container.enableGridMode = true
            binding.title.setTextOrUnknown(audio.getProperTitle())
            binding.secondaryDetail.setTextOrUnknown(audio.getProperArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.getProperAlbum())
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
            binding.title.setTextOrUnknown(audio.getProperTitle())
            binding.secondaryDetail.setTextOrUnknown(audio.getProperArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.getProperAlbum())
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
                    generalAdapterCallbacks?.onAlbumLongClicked(albums, albums.indexOf(album), binding.cover)
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
                    generalAdapterCallbacks?.onArtistLongClicked(artists, artists.indexOf(artist), binding.cover)
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

    /**
     * ViewHolder for composer result rows. Composers are modeled as [Artist] elsewhere
     * in the app (e.g. the Composers panel), so this holder mirrors [ArtistHolder].
     *
     * @param binding The view binding for the list item layout.
     */
    inner class ComposerHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.ComposerItem) {
            val composer = item.composer
            binding.title.setTextOrUnknown(composer.name)
            binding.secondaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, composer.trackCount, composer.trackCount)
            )
            binding.tertiaryDetail.gone(false)
            binding.cover.loadArtCoverWithPayload(item = composer)
            binding.container.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onComposerClicked(composers, composers.indexOf(composer), it)
                }
            }
            binding.container.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    generalAdapterCallbacks?.onComposerLongClicked(composers, composers.indexOf(composer), binding.cover)
                }
                true
            }
        }
    }

    /**
     * ViewHolder for year-group result rows.
     *
     * @param binding The view binding for the list item layout.
     */
    inner class YearHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: SearchAdapterItem.YearItem) {
            val yearGroup = item.yearGroup
            binding.title.text = yearGroup.year
            binding.secondaryDetail.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, yearGroup.songCount, yearGroup.songCount
            )
            binding.tertiaryDetail.gone(false)
            binding.cover.loadArtCoverWithPayload(yearGroup)
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onYearGroupClicked(yearGroup, it)
            }
        }
    }

    companion object {
        /** View type for section header rows (Songs, Albums, Artists, Genres, Composers, Years). */
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

        /** View type for composer result rows. */
        const val VIEW_TYPE_COMPOSER = 7

        /** View type for year-group result rows. */
        const val VIEW_TYPE_YEAR = 8
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

    /**
     * A composer result row. Composers are modeled as [Artist] elsewhere in the app.
     *
     * @param composer The composer (as [Artist]) data to display.
     */
    data class ComposerItem(val composer: Artist) : SearchAdapterItem()

    /**
     * A year-group result row.
     *
     * @param yearGroup The [YearGroup] data to display.
     */
    data class YearItem(val yearGroup: YearGroup) : SearchAdapterItem()
}
