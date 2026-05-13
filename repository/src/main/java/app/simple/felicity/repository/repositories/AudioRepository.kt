package app.simple.felicity.repository.repositories

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.sqlite.db.SimpleSQLiteQuery
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.YearGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing Audio data from the local database.
 * Provides methods to query, insert, delete, and manipulate audio records.
 */
@Singleton
class AudioRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /**
     * Get all audio files that have been marked as favorite, ordered by title.
     * Reads directly from the audio table's is_favorite column.
     */
    fun getFavoriteAudio(): Flow<List<Audio>> {
        return audioDatabase.audioDao()?.getFavoriteAudio()?.map { it.toList() }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    /**
     * Returns a live Flow of every song the user has flagged as "always skip".
     * The list updates automatically whenever a song is added or removed from the skip list —
     * no manual refresh required, the database just tells us whenever things change.
     */
    fun getAlwaysSkippedAudio(): Flow<List<Audio>> {
        return audioDatabase.audioDao()?.getAlwaysSkippedAudio()?.map { it.toList() }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    /**
     * Marks every song in [songs] as always-skip (or clears the flag) in a single
     * database operation — much faster than doing it one by one.
     *
     * @param songs  The list of songs to update.
     * @param skip   Pass true to flag them all, false to clear the flag.
     */
    suspend fun setAlwaysSkipBatch(songs: List<Audio>, skip: Boolean) = withContext(Dispatchers.IO) {
        val ids = songs.map { it.id }
        if (ids.isNotEmpty()) {
            audioDatabase.audioDao()?.setAlwaysSkipBatch(ids, skip)
        }
    }

    /**
     * Flips the favorite flag for a single song in the database.
     * Pass the song's id and whether it should be marked as a favorite or not.
     *
     * @param id         The database id of the song to update.
     * @param isFavorite True to mark as favorite, false to remove it.
     */
    suspend fun setFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.setFavorite(id, isFavorite)
    }

    /**
     * Minimum duration threshold in milliseconds derived from [LibraryPreferences].
     * The preference stores the value in seconds.
     */
    private fun minDurationMs(): Long =
        LibraryPreferences.getMinimumAudioLength().toLong() * 1000L

    /**
     * Minimum file-size threshold in bytes derived from [LibraryPreferences].
     * The preference stores the value in kilobytes (KB).
     */
    private fun minSizeBytes(): Long =
        LibraryPreferences.getMinimumAudioSize().toLong() * 1024L

    /**
     * Get all audio files from the database as a Flow.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getAllAudio(): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all audio files from the database as a list.
     * Results are filtered by [LibraryPreferences] minimum duration and size.
     */
    suspend fun getAllAudioList(): MutableList<Audio> = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.getFilteredAudioList(minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all unique artists from the database as a Flow.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getAllArtists(): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.getFilteredArtists(minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all unique albums from the database as a Flow.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getAllAlbums(): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.getFilteredAlbums(minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all albums with aggregated data including song counts and file paths.
     * This method groups audio files by album and creates proper Album objects.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of albums with complete metadata
     */
    fun getAllAlbumsWithAggregation(): Flow<List<Album>> {
        return audioDatabase.audioDao()?.getFilteredAudioForAlbumAggregation(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Group audio files by album name
            audioList.groupBy { it.album }
                .mapNotNull { (albumName, songs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null

                    val firstSong = songs.firstOrNull() ?: return@mapNotNull null

                    // Aggregate data from all songs in the album
                    val songPaths = songs.map { it.uri }
                    val years = songs.mapNotNull { it.year?.toLongOrNull() }.filter { it > 0 }

                    // Generate unique ID based on album name and artist to avoid collisions
                    val uniqueId = "${albumName}_${firstSong.artist}".hashCode().toLong()

                    Album(
                            id = uniqueId,
                            name = albumName,
                            artist = firstSong.artist,
                            artistId = firstSong.artist?.hashCode()?.toLong() ?: 0L,
                            songCount = songs.size,
                            firstYear = years.minOrNull() ?: 0,
                            lastYear = years.maxOrNull() ?: 0,
                            songPaths = songPaths
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all artists with aggregated data including album counts, track counts, and song paths.
     * This method groups audio files by artist and creates proper Artist objects.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of artists with complete metadata
     */
    fun getAllArtistsWithAggregation(): Flow<List<Artist>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            val splitRegex = Regex(ARTIST_SEPARATOR_REGEX, RegexOption.IGNORE_CASE)

            // We build two maps in a single pass over the song list so this stays fast
            // no matter how large the library is. Each song contributes to every individual
            // artist credited in its artist field (e.g. "AKON feat. WYCLEF" adds to both).
            val artistSongPaths = mutableMapOf<String, MutableList<String>>()
            val artistAlbums = mutableMapOf<String, MutableSet<String>>()

            audioList.forEach { audio ->
                val field = audio.artist ?: return@forEach
                field.split(splitRegex)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { name ->
                        artistSongPaths.getOrPut(name) { mutableListOf() }.add(audio.uri)
                        audio.album?.let { artistAlbums.getOrPut(name) { mutableSetOf() }.add(it) }
                    }
            }

            artistSongPaths.map { (name, paths) ->
                Artist(
                        id = name.hashCode().toLong(),
                        name = name,
                        albumCount = artistAlbums[name]?.size ?: 0,
                        trackCount = paths.size,
                        songPaths = paths
                )
            }.sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all composers with aggregated data including track counts and song paths.
     * Groups audio files by the composer tag and builds one entry per unique name.
     * Songs without a composer tag are quietly skipped.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     *
     * @return Flow of composers represented as [Artist] objects, sorted by name
     */
    fun getAllComposersWithAggregation(): Flow<List<Artist>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            val map = mutableMapOf<String, MutableList<Audio>>()
            audioList.forEach { audio ->
                val name = audio.composer?.takeIf { it.isNotBlank() } ?: return@forEach
                map.getOrPut(name) { mutableListOf() }.add(audio)
            }
            map.map { (name, songs) ->
                val uniqueAlbums = songs.mapNotNull { it.album }.distinct().size
                Artist(
                        id = name.hashCode().toLong(),
                        name = name,
                        albumCount = uniqueAlbums,
                        trackCount = songs.size,
                        songPaths = songs.map { it.uri }
                )
            }.sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all unique album artists from the database as a Flow, with aggregated
     * album counts, track counts, and song paths grouped by the album_artist tag.
     * Songs that have no album artist tag are quietly skipped — no tag, no entry.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     *
     * @return Flow of album artists with complete metadata, sorted by name
     */
    fun getAllAlbumArtistsWithAggregation(): Flow<List<Artist>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            buildAlbumArtistSongMap(audioList).map { (name, songs) ->
                Artist(
                        id = name.hashCode().toLong(),
                        name = name,
                        albumCount = songs.mapNotNull { it.album }.distinct().size,
                        trackCount = songs.size,
                        songPaths = songs.map { it.uri }
                )
            }.sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get page data for a specific album artist as a Flow.
     * Returns every song whose album_artist tag matches the given artist, along with
     * their albums and genres — basically everything you'd want to see on the artist page.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     *
     * @param albumArtist The [Artist] (acting as album artist) whose page data we want.
     * @return Flow of [PageData] with songs, albums, and genres for this album artist.
     */
    fun getAlbumArtistPageData(albumArtist: Artist): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Use the same split-based lookup as the list so the song count always matches.
            // We look the name up in the pre-built map rather than doing a raw equals check.
            val artistAudios = buildAlbumArtistSongMap(audioList)[albumArtist.name] ?: emptyList()

            // Pull out unique albums from those songs
            val albumsMap = artistAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null

                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = albumArtist.name ?: "",
                            artistId = albumArtist.id,
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            // Pull out unique genres from those songs
            val genresMap = artistAudios.groupBy { it.genre }
                .mapNotNull { (genreName, _) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null

                    val genreAllSongs = audioList.filter { it.genre == genreName }

                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = genreAllSongs.map { it.uri },
                            songCount = genreAllSongs.size
                    )
                }

            PageData(
                    songs = artistAudios,
                    albums = albumsMap,
                    genres = genresMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all genres with aggregated data including song counts and song paths.
     * This method groups audio files by genre and creates proper Genre objects.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of genres with complete metadata
     */
    fun getAllGenresWithAggregation(): Flow<List<Genre>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Group audio files by genre name
            audioList.groupBy { it.genre }
                .mapNotNull { (genreName, songs) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null

                    // Aggregate song paths from all songs in the genre
                    val songPaths = songs.map { it.uri }

                    // Generate unique ID based on genre name
                    val uniqueId = genreName.hashCode().toLong()

                    Genre(
                            id = uniqueId,
                            name = genreName,
                            songPaths = songPaths,
                            songCount = songs.size
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Builds a map of individual artist name → all songs that credit them, in a single
     * pass over [audioList]. Combined credits like "AKON feat. WYCLEF" are split so each
     * artist gets their own entry. Use this instead of grouping by the raw artist field
     * so that track counts are consistent with what the artist page shows.
     */
    private fun buildArtistSongMap(audioList: List<Audio>): Map<String, List<Audio>> {
        val splitRegex = Regex(ARTIST_SEPARATOR_REGEX, RegexOption.IGNORE_CASE)
        val map = mutableMapOf<String, MutableList<Audio>>()
        audioList.forEach { audio ->
            val field = audio.artist ?: return@forEach
            field.split(splitRegex)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { name -> map.getOrPut(name) { mutableListOf() }.add(audio) }
        }
        return map
    }

    /**
     * Same as [buildArtistSongMap] but reads the album_artist tag instead of the artist tag.
     * This keeps the album-artist list and page counts in sync with each other.
     */
    private fun buildAlbumArtistSongMap(audioList: List<Audio>): Map<String, List<Audio>> {
        val splitRegex = Regex(ARTIST_SEPARATOR_REGEX, RegexOption.IGNORE_CASE)
        val map = mutableMapOf<String, MutableList<Audio>>()
        audioList.forEach { audio ->
            val field = audio.albumArtist ?: return@forEach
            field.split(splitRegex)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { name -> map.getOrPut(name) { mutableListOf() }.add(audio) }
        }
        return map
    }

    /**
     * Extracts the document ID from a SAF document URI stored as the audio's URI string.
     * For example, "content://.../document/primary%3AMusic%2Fsong.mp3" → "primary:Music/song.mp3".
     * Returns null if the URI is not a proper SAF document URI (shouldn't happen in practice).
     */
    private fun docIdOf(uri: String): String? {
        return try {
            DocumentsContract.getDocumentId(uri.toUri())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get all unique folders that contain audio files, with aggregated data.
     * Groups audio files by their parent directory using the document ID from the SAF URI.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of folders with complete metadata
     */
    fun getAllFoldersWithAggregation(): Flow<List<Folder>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            audioList.groupBy { audio ->
                // The document ID is "primary:Music/Artist/Album/song.mp3" —
                // the parent folder is everything up to the last slash.
                val docId = docIdOf(audio.uri) ?: return@groupBy ""
                val lastSlash = docId.lastIndexOf('/')
                if (lastSlash > 0) docId.substring(0, lastSlash) else docId
            }.mapNotNull { (folderDocId, songs) ->
                if (folderDocId.isEmpty()) return@mapNotNull null

                // The display name is the segment after the last slash (or after ":" for roots).
                val folderName = folderDocId.substringAfterLast('/').let {
                    if (it.contains(':')) it.substringAfter(':') else it
                }.ifEmpty { folderDocId }

                Folder(
                        id = folderDocId.hashCode().toLong(),
                        path = folderDocId,
                        name = folderName,
                        songPaths = songs.map { it.uri },
                        songCount = songs.size
                )
            }.sortedBy { it.name.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Returns the top-level granted folders directly from the system's persisted SAF
     * permissions — no path math needed. Each entry in [persistedUriPermissions] IS
     * a top-level folder the user handed us. We just decode the tree document ID
     * (e.g. "primary:Music") and count how many songs live under each tree.
     */
    fun getTopLevelFolders(): Flow<List<Folder>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->

            // Grab the document IDs of all trees the user has granted read access to.
            val grantedTreeDocIds = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .mapNotNull { perm ->
                    try {
                        DocumentsContract.getTreeDocumentId(perm.uri)
                    } catch (_: Exception) {
                        null
                    }
                }
                .toSet()

            grantedTreeDocIds.mapNotNull { treeDocId ->
                // The display name is whatever comes after the last "/" or after the ":" for roots.
                val folderName = treeDocId.substringAfterLast('/').let {
                    if (it.contains(':')) it.substringAfter(':') else it
                }.ifEmpty { treeDocId }

                // Every audio file whose document ID starts with this tree belongs here.
                val songsUnder = audioList.filter { audio ->
                    val docId = docIdOf(audio.uri) ?: return@filter false
                    docId.startsWith("$treeDocId/") || docId == treeDocId
                }

                Folder(
                        id = treeDocId.hashCode().toLong(),
                        path = treeDocId,
                        name = folderName,
                        songPaths = songsUnder.map { it.uri },
                        songCount = songsUnder.size
                )
            }.sortedBy { it.name.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get the contents of a folder at the given document-ID path: immediate sub-folders
     * and the audio files sitting directly inside it (not in a sub-folder).
     *
     * [folderPath] is a SAF document ID like "primary:Music/Artist/Album". We extract the
     * document ID from each audio's URI and compare — no filesystem paths needed at all.
     *
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param folderPath The folder's document ID (as stored in [Folder.path]).
     * @return Flow of [FolderContents] with subFolders and songs
     */
    fun getFolderContents(folderPath: String): Flow<FolderContents> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->

            // Pair each audio with its parsed document ID once so we don't repeat the work.
            data class AudioEntry(val audio: Audio, val docId: String)

            val entries = audioList.mapNotNull { audio ->
                val docId = docIdOf(audio.uri) ?: return@mapNotNull null
                AudioEntry(audio, docId)
            }

            // Songs sitting directly in this folder — their parent docId == folderPath.
            val directSongs = entries
                .filter { it.docId.substringBeforeLast('/') == folderPath }
                .map { it.audio }

            // Everything deeper (used only for sub-folder discovery).
            val allUnder = entries.filter { it.docId.startsWith("$folderPath/") }

            // Immediate sub-folder document IDs — take only the first path segment below us.
            val subFolderDocIds = allUnder.mapNotNull { entry ->
                val relative = entry.docId.removePrefix("$folderPath/")
                val firstSlash = relative.indexOf('/')
                if (firstSlash > 0) "$folderPath/${relative.substring(0, firstSlash)}" else null
            }.toSet()

            val subFolders = subFolderDocIds.map { subDocId ->
                val subSongs = entries.filter {
                    it.docId.startsWith("$subDocId/") || it.docId.substringBeforeLast('/') == subDocId
                }
                Folder(
                        id = subDocId.hashCode().toLong(),
                        path = subDocId,
                        name = subDocId.substringAfterLast('/'),
                        songPaths = subSongs.map { it.audio.uri },
                        songCount = subSongs.size
                )
            }.sortedBy { it.name.lowercase() }

            FolderContents(subFolders = subFolders, songs = directSongs)
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Holds the browsable contents of a folder: immediate sub-folders and direct audio files.
     */
    data class FolderContents(
            val subFolders: List<Folder>,
            val songs: List<Audio>
    )

    /**
     * Get all data for an album page including songs, artists, and genres.
     * This method filters audio files by album name and aggregates related data.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param album The album to get data for
     * @return Flow of CollectionPageData with audios, artists, and genres
     */
    fun getAlbumPageData(album: Album): Flow<PageData> {
        val artistWhitelist: Set<String> = AudioRepository::class.java.getResourceAsStream(ARTIST_WHITELIST)
            ?.bufferedReader()?.use { it.readLines().map { name -> name.trim() }.toSet() } ?: emptySet()

        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Filter songs by album name (using album name instead of ID since we're using local DB)
            val albumAudios = audioList.filter { it.album == album.name }

            // Split combined artist names and create a map of split artists to their songs
            val artistToSongsMap = mutableMapOf<String, MutableList<Audio>>()

            albumAudios.forEach { audio ->
                val artistName = audio.albumArtist ?: return@forEach

                // Check if artist is in whitelist (shouldn't be split)
                if (artistWhitelist.any { it.equals(artistName, ignoreCase = true) }) {
                    artistToSongsMap.getOrPut(artistName) { mutableListOf() }.add(audio)
                } else {
                    // Split album artist names using the regex
                    val splitArtists = artistName.split(Regex(ARTIST_SEPARATOR_REGEX))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    // Add the song to each split album artist
                    splitArtists.forEach { splitArtist ->
                        artistToSongsMap.getOrPut(splitArtist) { mutableListOf() }.add(audio)
                    }
                }
            }

            // Now match split album artists to all their songs in the entire collection
            val artistsMap = artistToSongsMap.keys.map { artistName ->
                // Find all songs where this album artist is credited (even if not solo).
                // Single-word names get a whole-word check so "LISA" won't accidentally
                // match something like "CARALISA".
                val artistAllSongs = audioList.filter { audio ->
                    artistFieldMatchesName(audio.albumArtist, artistName)
                }

                // Count unique albums by this artist
                val uniqueAlbums = artistAllSongs.mapNotNull { it.album }.distinct().size

                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = uniqueAlbums,
                        trackCount = artistAllSongs.size,
                        songPaths = artistAllSongs.map { it.uri }
                )
            }.sortedBy { it.name?.lowercase() }

            // Extract unique genres from album songs
            val genresMap = albumAudios.groupBy { it.genre }
                .mapNotNull { (genreName, _) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null

                    // Count all songs for this genre in the entire collection
                    val genreAllSongs = audioList.filter { it.genre == genreName }

                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = genreAllSongs.map { it.uri },
                            songCount = genreAllSongs.size
                    )
                }

            PageData(
                    songs = albumAudios,
                    artists = artistsMap,
                    genres = genresMap
            )
        } ?: throw IllegalStateException("AudioDao is null") // TODO - shouldn't throw?
    }

    /**
     * Get page data for a specific composer as a Flow.
     * Returns all songs whose composer tag matches the given name, along with
     * their albums and genres — everything you'd want to see on a composer page.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     *
     * @param composer The [Artist] (acting as a composer) whose page data we want.
     * @return Flow of [PageData] with songs, albums, and genres for this composer.
     */
    fun getComposerPageData(composer: Artist): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            val composerAudios = audioList.filter { audio ->
                audio.composer?.trim().equals(composer.name?.trim(), ignoreCase = true)
            }

            val albumsMap = composerAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null
                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = composer.name ?: "",
                            artistId = composer.id,
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            val genresMap = composerAudios.groupBy { it.genre }
                .mapNotNull { (genreName, _) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null
                    val genreAllSongs = audioList.filter { it.genre == genreName }
                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = genreAllSongs.map { it.uri },
                            songCount = genreAllSongs.size
                    )
                }

            PageData(
                    songs = composerAudios,
                    albums = albumsMap,
                    genres = genresMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get page data for a specific artist as a Flow.
     * Returns songs, albums, and genres associated with the artist.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getArtistPageData(artist: Artist): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            val artistAudios = audioList.filter { audio ->
                artistFieldMatchesName(audio.artist, artist.name ?: "")
            }

            // Extract unique albums from artist songs
            val albumsMap = artistAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null

                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = artist.name ?: "",
                            artistId = artist.id,
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            // Extract unique genres from artist songs
            val genresMap = artistAudios.groupBy { it.genre }
                .mapNotNull { (genreName, _) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null

                    // Count all songs for this genre in the entire collection
                    val genreAllSongs = audioList.filter { it.genre == genreName }

                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = genreAllSongs.map { it.uri },
                            songCount = genreAllSongs.size
                    )
                }

            PageData(
                    songs = artistAudios,
                    albums = albumsMap,
                    genres = genresMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get page data for a specific genre as a Flow.
     * Returns songs, albums, and artists associated with the genre.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getGenrePageData(genre: Genre): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Filter songs by genre name
            val genreAudios = audioList.filter { it.genre == genre.name }

            // Extract unique artists from genre songs.
            // We build the full artist map once so each name's track count covers
            // their entire library presence, not just songs in this genre.
            val fullArtistMap = buildArtistSongMap(audioList)
            val artistsMap = buildArtistSongMap(genreAudios).keys.mapNotNull { artistName ->
                val allSongs = fullArtistMap[artistName] ?: return@mapNotNull null
                val uniqueAlbums = allSongs.mapNotNull { it.album }.distinct().size
                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = uniqueAlbums,
                        trackCount = allSongs.size,
                        songPaths = allSongs.map { it.uri }
                )
            }

            // Extract unique albums from genre songs
            val albumsMap = genreAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null

                    // Get primary artist for this album
                    val primaryArtist = albumSongs.firstOrNull()?.artist ?: ""

                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = primaryArtist,
                            artistId = primaryArtist.hashCode().toLong(),
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            PageData(
                    songs = genreAudios,
                    albums = albumsMap,
                    artists = artistsMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get page data for a specific folder as a Flow.
     * Returns all songs in the folder plus aggregated albums, artists and genres.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param folder The folder to get data for
     * @return Flow of PageData with songs, albums, artists, and genres
     */
    fun getFolderPageData(folder: Folder): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Filter songs whose parent document ID matches the folder's path.
            // folder.path is a document ID like "primary:Music/Artist/Album".
            val folderAudios = audioList.filter { audio ->
                val docId = docIdOf(audio.uri) ?: return@filter false
                docId.substringBeforeLast('/') == folder.path
            }

            // Extract unique albums from folder songs
            val albumsMap = folderAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null
                    val primaryArtist = albumSongs.firstOrNull()?.artist ?: ""
                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = primaryArtist,
                            artistId = primaryArtist.hashCode().toLong(),
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            // Extract unique artists from folder songs, counting their full library totals.
            val fullArtistMap = buildArtistSongMap(audioList)
            val artistsMap = buildArtistSongMap(folderAudios).keys.mapNotNull { artistName ->
                val allSongs = fullArtistMap[artistName] ?: return@mapNotNull null
                val uniqueAlbums = allSongs.mapNotNull { it.album }.distinct().size
                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = uniqueAlbums,
                        trackCount = allSongs.size,
                        songPaths = allSongs.map { it.uri }
                )
            }

            // Extract unique genres from folder songs
            val genresMap = folderAudios.groupBy { it.genre }
                .mapNotNull { (genreName, _) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null
                    val genreAllSongs = audioList.filter { it.genre == genreName }
                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = genreAllSongs.map { it.uri },
                            songCount = genreAllSongs.size
                    )
                }

            PageData(
                    songs = folderAudios,
                    albums = albumsMap,
                    artists = artistsMap,
                    genres = genresMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all unique year groups that contain audio files, with aggregated data.
     * This method groups audio files by their year tag and creates YearGroup objects.
     * Songs with no year are grouped under a special "Unknown" year.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of year groups with complete metadata
     */
    fun getAllYearsWithAggregation(): Flow<List<YearGroup>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            audioList.groupBy { audio ->
                audio.year?.takeIf { it.isNotBlank() } ?: "Unknown"
            }.map { (year, songs) ->
                val songPaths = songs.map { it.uri }
                val uniqueId = year.hashCode().toLong()
                YearGroup(
                        id = uniqueId,
                        year = year,
                        songPaths = songPaths,
                        songCount = songs.size
                )
            }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get page data for a specific year group as a Flow.
     * Returns all songs tagged with this year plus aggregated albums and artists.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param yearGroup The year group to get data for
     * @return Flow of PageData with songs, albums, and artists
     */
    fun getYearPageData(yearGroup: YearGroup): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            val yearAudios = if (yearGroup.year == "Unknown") {
                audioList.filter { it.year.isNullOrBlank() }
            } else {
                audioList.filter { it.year == yearGroup.year }
            }

            val albumsMap = yearAudios.groupBy { it.album }
                .mapNotNull { (albumName, albumSongs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null
                    val primaryArtist = albumSongs.firstOrNull()?.artist ?: ""
                    Album(
                            id = albumName.hashCode().toLong(),
                            name = albumName,
                            artist = primaryArtist,
                            artistId = primaryArtist.hashCode().toLong(),
                            songCount = albumSongs.size,
                            songPaths = albumSongs.map { it.uri }
                    )
                }

            val fullArtistMap = buildArtistSongMap(audioList)
            val artistsMap = buildArtistSongMap(yearAudios).keys.mapNotNull { artistName ->
                val allSongs = fullArtistMap[artistName] ?: return@mapNotNull null
                val uniqueAlbums = allSongs.mapNotNull { it.album }.distinct().size
                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = uniqueAlbums,
                        trackCount = allSongs.size,
                        songPaths = allSongs.map { it.uri }
                )
            }

            PageData(
                    songs = yearAudios,
                    albums = albumsMap,
                    // artists = artistsMap
            )
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get recent audio files from the database as a Flow.
     * Returns all audio files added in the last 30 days, ordered by date added descending.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getRecentAudio(): Flow<MutableList<Audio>> {
        val thirtyDaysAgoMs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return audioDatabase.audioDao()?.getFilteredRecentAudio(minDurationMs(), minSizeBytes(), thirtyDaysAgoMs)
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get all audio files by a specific artist as a Flow.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param artist The name of the artist
     * @return Flow of audio files by the specified artist, sorted by title
     */
    fun getAudioByArtist(artist: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.getFilteredAudioByArtist(artist, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get audio ID by file path
     * @param path The file path of the audio
     * @return The ID of the audio file
     */
    suspend fun getAudioIdByPath(path: String): Long = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.getAudioIdByPath(path)
            ?: throw IllegalStateException("AudioDao is null or audio not found")
    }

    /**
     * Execute a raw SQL query on the audio database
     * @param query The SQL query string
     * @param args Optional query arguments
     * @return List of audio files matching the query
     */
    suspend fun executeRawQuery(query: String, args: Array<Any>? = null): MutableList<Audio> = withContext(Dispatchers.IO) {
        val sqlQuery = SimpleSQLiteQuery(query, args)
        audioDatabase.audioDao()?.getQueriedData(sqlQuery)
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Insert an audio file into the database
     * @param audio The audio object to insert
     */
    suspend fun insertAudio(audio: Audio) = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.insert(audio)
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Insert multiple audio files into the database
     * @param audioList List of audio objects to insert
     */
    suspend fun insertAudioBatch(audioList: List<Audio>) = withContext(Dispatchers.IO) {
        audioList.forEach { audio ->
            audioDatabase.audioDao()?.insert(audio)
        }
    }

    /**
     * Delete an audio file from the database
     * @param audio The audio object to delete
     */
    suspend fun deleteAudio(audio: Audio) = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.delete(audio)
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Delete all audio files from the database (nuke the table)
     */
    suspend fun clearAllAudio() = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.nukeTable()
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Check if the database is initialized and accessible
     * @return true if the database is ready to use
     */
    fun isDatabaseReady(): Boolean {
        return try {
            audioDatabase.isOpen && audioDatabase.audioDao() != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get the count of audio files in the database
     * @return The total number of audio files
     */
    suspend fun getAudioCount(): Int = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.getAllAudioList()?.size ?: 0
    }

    /**
     * Search audio files by title (one-shot, kept for compatibility).
     */
    suspend fun searchByTitle(title: String): MutableList<Audio> = withContext(Dispatchers.IO) {
        val query = "SELECT * FROM audio WHERE title LIKE ? ORDER BY title COLLATE NOCASE ASC"
        val args = arrayOf<Any>("%$title%")
        executeRawQuery(query, args)
    }

    /**
     * Search audio files by artist (one-shot, kept for compatibility).
     */
    suspend fun searchByArtist(artist: String): MutableList<Audio> = withContext(Dispatchers.IO) {
        val query = "SELECT * FROM audio WHERE artist LIKE ? ORDER BY title COLLATE NOCASE ASC"
        val args = arrayOf<Any>("%$artist%")
        executeRawQuery(query, args)
    }

    /**
     * Search audio files by album (one-shot, kept for compatibility).
     */
    suspend fun searchByAlbum(album: String): MutableList<Audio> = withContext(Dispatchers.IO) {
        val query = "SELECT * FROM audio WHERE album LIKE ? ORDER BY title COLLATE NOCASE ASC"
        val args = arrayOf<Any>("%$album%")
        executeRawQuery(query, args)
    }

    /**
     * Reactive search by title – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByTitleFlow(title: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByTitleFiltered(title, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Reactive search by artist – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByArtistFlow(artist: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByArtistFiltered(artist, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Reactive artist search that returns proper [Artist] objects with accurate counts.
     *
     * The SQL query finds every song whose artist field contains [query], then we split
     * the combined credits (e.g. "AKON feat. WYCLEF") so each individual name gets its
     * own entry. We also filter out names that don't actually contain the query string
     * so a search for "AKON" doesn't surface "WYCLEF" as a result just because they
     * collaborated on a track.
     *
     * @param query The text the user typed in the search box.
     * @return Flow of [Artist] objects whose names match [query], sorted by name.
     */
    fun searchArtistsFlow(query: String): Flow<List<Artist>> {
        return audioDatabase.audioDao()?.searchByArtistFiltered(query, minDurationMs(), minSizeBytes())?.map { songs ->
            buildArtistSongMap(songs)
                .filterKeys { it.contains(query, ignoreCase = true) }
                .map { (name, matchedSongs) ->
                    val uniqueAlbums = matchedSongs.mapNotNull { it.album }.distinct().size
                    Artist(
                            id = name.hashCode().toLong(),
                            name = name,
                            albumCount = uniqueAlbums,
                            trackCount = matchedSongs.size,
                            songPaths = matchedSongs.map { it.uri }
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Reactive search by album – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByAlbumFlow(album: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByAlbumFiltered(album, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Reactive search by genre – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByGenreFlow(genre: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByGenreFiltered(genre, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Reactive search by composer – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByComposerFlow(composer: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByComposerFiltered(composer, minDurationMs(), minSizeBytes())
            ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Get audio files sorted by a specific column
     * @param sortColumn The column to sort by (e.g., "title", "artist", "date_added")
     * @param ascending Whether to sort in ascending order (true) or descending (false)
     * @return List of sorted audio files
     */
    suspend fun getAudioSorted(sortColumn: String, ascending: Boolean = true): MutableList<Audio> = withContext(Dispatchers.IO) {
        val order = if (ascending) "ASC" else "DESC"
        val query = "SELECT * FROM audio ORDER BY $sortColumn COLLATE NOCASE $order"
        executeRawQuery(query, null)
    }

    /**
     * Get audio files by genre
     * @param genre The genre name
     * @return List of audio files in the specified genre
     */
    suspend fun getAudioByGenre(genre: String): MutableList<Audio> = withContext(Dispatchers.IO) {
        val query = "SELECT * FROM audio WHERE genre = ? ORDER BY title COLLATE NOCASE ASC"
        val args = arrayOf<Any>(genre)
        executeRawQuery(query, args)
    }

    /**
     * Returns the [Audio] row with the given auto-increment [id], or {@code null} if not found.
     *
     * @param id The Room-assigned primary key of the audio row.
     */
    suspend fun getAudioById(id: Long): Audio? = withContext(Dispatchers.IO) {
        audioDatabase.audioDao()?.getAudioById(id)
    }

    /**
     * Get audio files by album ID
     * @param albumId The album ID
     * @return List of audio files in the specified album
     */
    suspend fun getAudioByAlbumId(albumId: Long): MutableList<Audio> = withContext(Dispatchers.IO) {
        val query = "SELECT * FROM audio WHERE album_id = ? ORDER BY track ASC"
        val args = arrayOf<Any>(albumId)
        executeRawQuery(query, args)
    }

    companion object {
        /**
         * A comprehensive regular expression used to tokenize and split raw artist metadata strings
         * (e.g., from ID3 tags) into individual artist entities.
         *
         * This regex intercepts a wide variety of standard and non-standard delimiters commonly found
         * in messy audio metadata, capturing both punctuation and common collaborative text markers.
         *
         * **Whitespace Handling:**
         * The regex handles surrounding whitespace dynamically to ensure clean splits without leaving
         * leading or trailing spaces. Punctuation-based delimiters tolerate zero or more spaces (`\s*`),
         * while text/character-based delimiters require at least one space (`\s+`) to prevent
         * accidentally splitting mid-word (e.g., preventing 'x' from splitting "Lil Nas X").
         *
         * **Matched Delimiters:**
         *
         * *Punctuation Separators (Optional Surrounding Whitespace):*
         * * `[;,+/\\|]` : Matches semicolon, comma, plus, forward slash, backslash, or pipe.
         *
         * *Text & Symbol Separators (Mandatory Surrounding Whitespace):*
         * * `&` : Ampersand (requires spaces to protect edge cases like "A&M").
         * * `and`, `with`, `w/` : Standard conjunctions.
         * * `vs`, `vs.` : Versus indicators (period is optional).
         * * `x` : Collaboration indicator (e.g., "Artist A x Artist B").
         * * `feat`, `feat.`, `ft`, `ft.`, `featuring` : Guest appearance indicators (periods optional).
         * * `pres`, `pres.` : Presenter indicators (period is optional).
         * * `starring` : Theatrical or guest indicators.
         *
         * **Warning:**
         * This is an aggressive split. To protect officially recognized band names that legitimately
         * contain these characters (such as "AC/DC", "Earth, Wind & Fire", or "Florence + The Machine"),
         * the raw string should be evaluated against an artist whitelist prior to executing this regex.
         */
        private const val ARTIST_SEPARATOR_REGEX = "\\s*[;,+/\\\\|]\\s*|\\s+&\\s+|\\s+and\\s+|\\s+with\\s+|\\s+w/\\s+|\\s+vs\\.?\\s+|\\s+x\\s+" +
                "|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+pres\\.?\\s+|\\s+starring\\s+"

        private const val ARTIST_WHITELIST = "/artist_whitelist.txt"

        /**
         * Checks whether an audio file's artist field actually refers to [name].
         *
         * Multi-word names (e.g. "Daft Punk") use a simple case-insensitive substring check
         * because they're naturally specific enough not to appear inside another name by accident.
         *
         * Single-word names (e.g. "LISA" or "AKON") are trickier — a plain substring search
         * would match "CARALISA", and a word-boundary check would still match "LISA GERRARD"
         * or "PINK LISA". Instead, we split the artist field by the same delimiters used
         * everywhere else and require one of the resulting pieces to be an exact (case-insensitive)
         * match. This way "LISA" only hits a field where LISA is a standalone credit.
         */
        fun artistFieldMatchesName(artistField: String?, name: String): Boolean {
            if (artistField == null || name.isEmpty()) return false
            return if (!name.contains(' ')) {
                val splitRegex = Regex(ARTIST_SEPARATOR_REGEX, RegexOption.IGNORE_CASE)
                artistField.split(splitRegex)
                    .any { it.trim().equals(name, ignoreCase = true) }
            } else {
                artistField.contains(name, ignoreCase = true)
            }
        }
    }
}