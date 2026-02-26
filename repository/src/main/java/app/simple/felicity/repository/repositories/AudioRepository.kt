package app.simple.felicity.repository.repositories

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
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
                    val songPaths = songs.map { it.path }
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
            // Group audio files by artist name
            audioList.groupBy { it.artist }
                .mapNotNull { (artistName, songs) ->
                    if (artistName.isNullOrEmpty()) return@mapNotNull null

                    // Count unique albums by this artist
                    val uniqueAlbums = songs.mapNotNull { it.album }.distinct().size

                    // Aggregate song paths from all songs by the artist
                    val songPaths = songs.map { it.path }

                    // Generate unique ID based on artist name
                    val uniqueId = artistName.hashCode().toLong()

                    Artist(
                            id = uniqueId,
                            name = artistName,
                            albumCount = uniqueAlbums,
                            trackCount = songs.size,
                            songPaths = songPaths
                    )
                }
                .sortedBy { it.name?.lowercase() }
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
                    val songPaths = songs.map { it.path }

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
     * Get all unique folders that contain audio files, with aggregated data.
     * This method groups audio files by their parent directory path.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @return Flow of folders with complete metadata
     */
    fun getAllFoldersWithAggregation(): Flow<List<Folder>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            audioList.groupBy { audio ->
                val filePath = audio.path ?: return@groupBy ""
                val lastSlash = filePath.lastIndexOf('/')
                if (lastSlash > 0) filePath.substring(0, lastSlash) else filePath
            }.mapNotNull { (folderPath, songs) ->
                if (folderPath.isEmpty()) return@mapNotNull null

                val folderName = folderPath.substringAfterLast('/')
                    .ifEmpty { folderPath }
                val songPaths = songs.map { it.path }
                val uniqueId = folderPath.hashCode().toLong()

                Folder(
                        id = uniqueId,
                        path = folderPath,
                        name = folderName,
                        songPaths = songPaths,
                        songCount = songs.size
                )
            }.sortedBy { it.name.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    //    /**
    //     * Get the root-level folders for the file-tree browser.
    //     *
    //     * Algorithm:
    //     *  1. Collect every unique parent-directory path of all audio files.
    //     *  2. Find the longest common path prefix shared by ALL of those directories
    //     *     (the true root of the tree — e.g. "/storage/emulated/0").
    //     *  3. Return the immediate children of that common root so the user starts
    //     *     drilling from the topmost meaningful directory.
    //     *
    //     * Example: if all audio lives under /storage/emulated/0/Music/** the root
    //     * returned is [/storage/emulated/0/Music].  If audio spans both
    //     * /storage/emulated/0/Music and /storage/emulated/0/Downloads the root
    //     * returned is [/storage/emulated/0/Music, /storage/emulated/0/Downloads].
    //     */
    fun getTopLevelFolders(): Flow<List<Folder>> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // All unique parent-directory paths (the folder that directly contains each file)
            val allFolderPaths = audioList.mapNotNull { audio ->
                val filePath = audio.path ?: return@mapNotNull null
                val lastSlash = filePath.lastIndexOf('/')
                if (lastSlash > 0) filePath.substring(0, lastSlash) else null
            }.toSet()

            if (allFolderPaths.isEmpty()) return@map emptyList()

            // Compute the longest common path prefix across all folder paths
            val commonRoot = findCommonPathPrefix(allFolderPaths)

            // Immediate children of the common root:
            // For each folder path, take only the first segment beyond commonRoot.
            val topLevelPaths = allFolderPaths.mapNotNull { path ->
                val relative = if (commonRoot.isEmpty()) path else path.removePrefix("$commonRoot/")
                // If relative == path the path IS the commonRoot (songs directly inside it)
                if (relative == path && commonRoot.isNotEmpty()) return@mapNotNull commonRoot
                val firstSegment = relative.substringBefore('/')
                if (firstSegment.isEmpty()) null
                else if (commonRoot.isEmpty()) "/$firstSegment" else "$commonRoot/$firstSegment"
            }.toSet()

            topLevelPaths.map { topPath ->
                val songsUnder = audioList.filter { audio ->
                    val p = audio.path ?: return@filter false
                    p.startsWith("$topPath/") || p.substringBeforeLast('/') == topPath
                }
                Folder(
                        id = topPath.hashCode().toLong(),
                        path = topPath,
                        name = topPath.substringAfterLast('/').ifEmpty { topPath },
                        songPaths = songsUnder.map { it.path },
                        songCount = songsUnder.size
                )
            }.sortedBy { it.name.lowercase() }
        } ?: throw IllegalStateException("AudioDao is null")
    }

    /**
     * Finds the longest common path prefix for a set of absolute paths.
     * Returns the common ancestor directory path, e.g.:
     *   {"/a/b/c", "/a/b/d"} → "/a/b"
     *   {"/a/b/c"} → "/a/b/c"  (single path is its own prefix)
     */
    private fun findCommonPathPrefix(paths: Set<String>): String {
        if (paths.size == 1) return paths.first()
        val segmentLists = paths.map { path -> path.split('/').filter { seg -> seg.isNotEmpty() } }
        val minLen = segmentLists.minOf { segs -> segs.size }
        val commonSegments = mutableListOf<String>()
        for (index in 0 until minLen) {
            val segment = segmentLists[0][index]
            if (segmentLists.all { segs -> segs[index] == segment }) {
                commonSegments.add(segment)
            } else {
                break
            }
        }
        return if (commonSegments.isEmpty()) "" else "/${commonSegments.joinToString("/")}"
    }

    /**
     * Get the contents of a folder at the given path: immediate sub-folders and direct audio files.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     * @param folderPath The folder path to explore
     * @return Flow of [FolderContents] with subFolders and songs
     */
    fun getFolderContents(folderPath: String): Flow<FolderContents> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Songs directly inside this folder (not in a sub-folder)
            val directSongs = audioList.filter { audio ->
                val path = audio.path ?: return@filter false
                path.substringBeforeLast('/') == folderPath
            }

            // All audio files inside sub-folders (path starts with folderPath/)
            val allSongsUnder = audioList.filter { audio ->
                audio.path?.startsWith("$folderPath/") == true
            }

            // Compute immediate sub-folder paths
            val subFolderPaths = allSongsUnder.mapNotNull { audio ->
                val path = audio.path ?: return@mapNotNull null
                val relative = path.removePrefix("$folderPath/")
                val firstSlash = relative.indexOf('/')
                if (firstSlash > 0) "$folderPath/${relative.substring(0, firstSlash)}" else null
            }.toSet()

            // Build Folder objects for each immediate sub-folder
            val subFolders = subFolderPaths.map { subPath ->
                val subSongs = audioList.filter { audio ->
                    val p = audio.path ?: return@filter false
                    p.startsWith("$subPath/") || p.substringBeforeLast('/') == subPath
                }
                Folder(
                        id = subPath.hashCode().toLong(),
                        path = subPath,
                        name = subPath.substringAfterLast('/'),
                        songPaths = subSongs.map { it.path },
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
                val artistName = audio.artist ?: return@forEach

                // Check if artist is in whitelist (shouldn't be split)
                if (artistWhitelist.any { it.equals(artistName, ignoreCase = true) }) {
                    artistToSongsMap.getOrPut(artistName) { mutableListOf() }.add(audio)
                } else {
                    // Split artist names using the regex
                    val splitArtists = artistName.split(Regex(ARTIST_REGEX))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    // Add the song to each split artist
                    splitArtists.forEach { splitArtist ->
                        artistToSongsMap.getOrPut(splitArtist) { mutableListOf() }.add(audio)
                    }
                }
            }

            // Now match split artists to all their songs in the entire collection using contains
            val artistsMap = artistToSongsMap.keys.map { artistName ->
                // Find all songs where this artist is involved (even if not solo)
                val artistAllSongs = audioList.filter { audio ->
                    audio.artist?.contains(artistName, ignoreCase = true) == true
                }

                // Count unique albums by this artist
                val uniqueAlbums = artistAllSongs.mapNotNull { it.album }.distinct().size

                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = uniqueAlbums,
                        trackCount = artistAllSongs.size,
                        songPaths = artistAllSongs.map { it.path }
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
                            songPaths = genreAllSongs.map { it.path },
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
     * Get page data for a specific artist as a Flow.
     * Returns songs, albums, and genres associated with the artist.
     * Results are filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getArtistPageData(artist: Artist): Flow<PageData> {
        return audioDatabase.audioDao()?.getFilteredAudio(minDurationMs(), minSizeBytes())?.map { audioList ->
            // Filter songs where artist name contains the specified artist (handles split artists)
            val artistAudios = audioList.filter { audio ->
                audio.artist?.contains(artist.name ?: "", ignoreCase = true) == true
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
                            songPaths = albumSongs.map { it.path }
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
                            songPaths = genreAllSongs.map { it.path },
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

            // Extract unique artists from genre songs
            val artistsMap = genreAudios.groupBy { it.artist }
                .mapNotNull { (artistName, _) ->
                    if (artistName.isNullOrEmpty()) return@mapNotNull null

                    // Count unique albums by this artist in the entire collection
                    val artistAllSongs = audioList.filter { it.artist == artistName }
                    val uniqueAlbums = artistAllSongs.mapNotNull { it.album }.distinct().size

                    Artist(
                            id = artistName.hashCode().toLong(),
                            name = artistName,
                            albumCount = uniqueAlbums,
                            trackCount = artistAllSongs.size,
                            songPaths = artistAllSongs.map { it.path }
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
                            songPaths = albumSongs.map { it.path }
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
            // Filter songs whose path starts with the folder path
            val folderAudios = audioList.filter { audio ->
                val parent = audio.path?.let {
                    val idx = it.lastIndexOf('/')
                    if (idx > 0) it.substring(0, idx) else it
                }
                parent == folder.path
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
                            songPaths = albumSongs.map { it.path }
                    )
                }

            // Extract unique artists from folder songs
            val artistsMap = folderAudios.groupBy { it.artist }
                .mapNotNull { (artistName, _) ->
                    if (artistName.isNullOrEmpty()) return@mapNotNull null
                    val artistAllSongs = audioList.filter { it.artist == artistName }
                    val uniqueAlbums = artistAllSongs.mapNotNull { it.album }.distinct().size
                    Artist(
                            id = artistName.hashCode().toLong(),
                            name = artistName,
                            albumCount = uniqueAlbums,
                            trackCount = artistAllSongs.size,
                            songPaths = artistAllSongs.map { it.path }
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
                            songPaths = genreAllSongs.map { it.path },
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
     * Get recent audio files from the database as a Flow.
     * Returns the 25 most recently added audio files.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun getRecentAudio(): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.getFilteredRecentAudio(minDurationMs(), minSizeBytes())
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
     * Reactive search by album – re-emits whenever the audio table changes.
     * Filtered in real-time by [LibraryPreferences] minimum duration and size.
     */
    fun searchByAlbumFlow(album: String): Flow<MutableList<Audio>> {
        return audioDatabase.audioDao()?.searchByAlbumFiltered(album, minDurationMs(), minSizeBytes())
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
        private const val ARTIST_REGEX = "\\s*[,&+/\\\\|]\\s*|\\s+and\\s+|\\s+with\\s+|\\s+w/\\s+|\\s+vs\\.?\\s+|\\s+x\\s+" +
                "|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+pres\\.?\\s+|\\s+starring\\s+"
        private const val ARTIST_WHITELIST = "/artist_whitelist.txt"
    }
}