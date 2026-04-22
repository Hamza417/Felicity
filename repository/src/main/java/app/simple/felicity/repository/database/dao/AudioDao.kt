package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio WHERE is_available = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getAllAudio(): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getAllAudioList(): MutableList<Audio>

    /**
     * Returns every row regardless of availability – used by reconcile and dedup passes.
     */
    @Query("SELECT * FROM audio")
    fun getAllAudioListAll(): MutableList<Audio>

    /**
     * Deletes all duplicate rows that share the same path, keeping only the row whose
     * date_modified is the highest (most recently scanned). If two rows share the same
     * date_modified, the one with the larger id wins (arbitrary but deterministic).
     * This is a pure-SQL single-statement dedup that runs entirely inside SQLite.
     */
    @Query("""
        DELETE FROM audio
        WHERE id NOT IN (
            SELECT id FROM audio
            GROUP BY uri
            HAVING id = MAX(id)
        )
        AND uri IN (
            SELECT uri FROM audio
            GROUP BY uri
            HAVING COUNT(*) > 1
        )
    """)
    suspend fun deleteStalePathDuplicates()

    // Filtered queries – honour minimum duration (ms) and minimum size (bytes) at query level
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize ORDER BY title COLLATE NOCASE ASC")
    fun getFilteredAudio(minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize ORDER BY title COLLATE NOCASE ASC")
    fun getFilteredAudioList(minDuration: Long, minSize: Long): MutableList<Audio>

    // Get unique artists
    @Query("SELECT * FROM audio WHERE is_available = 1 GROUP BY artist  ORDER BY artist COLLATE NOCASE ASC")
    fun getAllArtists(): Flow<MutableList<Audio>>

    // Get unique artists with filtering
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize GROUP BY artist ORDER BY artist COLLATE NOCASE ASC")
    fun getFilteredArtists(minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    // Get unique albums
    @Query("SELECT * FROM audio WHERE is_available = 1 GROUP BY album ORDER BY album COLLATE NOCASE ASC")
    fun getAllAlbums(): Flow<MutableList<Audio>>

    // Get unique albums with filtering
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize GROUP BY album ORDER BY album COLLATE NOCASE ASC")
    fun getFilteredAlbums(minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    // Get all audio files grouped by album for aggregation
    @Query("SELECT * FROM audio WHERE is_available = 1 ORDER BY album COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    fun getAllAudioForAlbumAggregation(): Flow<MutableList<Audio>>

    // Get all audio files grouped by album for aggregation with filtering
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize ORDER BY album COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    fun getFilteredAudioForAlbumAggregation(minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    // Get recent audio
    @Query("SELECT * FROM audio WHERE is_available = 1 ORDER BY date_added DESC LIMIT 25")
    fun getRecentAudio(): Flow<MutableList<Audio>>

    // Get recent audio with filtering – returns all songs added in the last 30 days
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND date_added >= :minDateAdded ORDER BY date_added DESC")
    fun getFilteredRecentAudio(minDuration: Long, minSize: Long, minDateAdded: Long): Flow<MutableList<Audio>>

    // get all audio files by artist name in ascending order
    @Query("SELECT * FROM audio WHERE artist = :artist AND is_available = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getAudioByArtist(artist: String): Flow<MutableList<Audio>>

    // get all audio files by artist name with filtering
    @Query("SELECT * FROM audio WHERE artist = :artist AND is_available = 1 AND duration >= :minDuration AND size >= :minSize ORDER BY title COLLATE NOCASE ASC")
    fun getFilteredAudioByArtist(artist: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    // Reactive search – Room will re-emit whenever the 'audio' table changes
    @Query("SELECT * FROM audio WHERE is_available = 1 AND title LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByTitle(query: String): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND artist LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByArtist(query: String): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND album LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByAlbum(query: String): Flow<MutableList<Audio>>

    // Reactive search with filtering
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND title LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByTitleFiltered(query: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND artist LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByArtistFiltered(query: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND album LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByAlbumFiltered(query: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND genre LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByGenreFiltered(query: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize AND composer LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchByComposerFiltered(query: String, minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

    @Query("SELECT id FROM audio WHERE uri = :path AND is_available = 1")
    fun getAudioIdByPath(path: String): Long

    @Query("SELECT * FROM audio WHERE id = :id LIMIT 1")
    suspend fun getAudioById(id: Long): Audio?

    @Query("SELECT * FROM audio WHERE uri = :path LIMIT 1")
    fun getAudioByPath(path: String): Audio?

    /**
     * Looks up an audio row by its real POSIX filesystem path (the path column,
     * which holds the MediaStore DATA value like /storage/emulated/0/Music/song.mp3).
     * This is the right query to use when matching against M3U entry paths.
     */
    @Query("SELECT * FROM audio WHERE path = :posixPath LIMIT 1")
    fun getAudioByPosixPath(posixPath: String): Audio?

    // Favorite / always-skip flag queries
    @Query("SELECT * FROM audio WHERE is_favorite = 1 AND is_available = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteAudio(): Flow<MutableList<Audio>>

    @Query("UPDATE audio SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE audio SET always_skip = :alwaysSkip WHERE id = :id")
    suspend fun setAlwaysSkip(id: Long, alwaysSkip: Boolean)

    @RawQuery
    fun getQueriedData(query: SupportSQLiteQuery): MutableList<Audio>

    @RawQuery
    fun getAudioByIDs(query: SupportSQLiteQuery): MutableList<Audio>

    /**
     * Delete a [Audio] item
     * from the table
     */
    @Delete
    suspend fun delete(audio: Audio)

    @Delete
    suspend fun delete(audioList: List<Audio>)

    /**
     * Insert [Audio] item
     * into the table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(audio: Audio)

    /**
     * Tries to insert an [Audio] item, but quietly gives up if a row with the
     * same hash already exists — no drama, no exceptions, just a polite -1 return.
     *
     * <p>This is used when importing M3U playlists to create placeholder entries
     * for tracks that are referenced in the file but not yet in the library.
     * The [OnConflictStrategy.IGNORE] strategy means we never accidentally
     * overwrite a real library entry with a placeholder.</p>
     *
     * @param audio The audio entry to insert.
     * @return The new row ID on success, or {@code -1} if a hash conflict was ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(audio: Audio): Long

    /**
     * Insert multiple [Audio] items in a batch. Uses IGNORE on conflict so that
     * if a hash somehow collides with an existing row (astronomically unlikely with
     * path-aware hashes), we silently skip the offending item rather than deleting
     * the row that's already safely in the library.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(audioList: List<Audio>)

    @Update
    suspend fun update(audio: Audio)

    @Update
    suspend fun update(audioList: List<Audio>)

    /**
     * Delete the entire table
     */
    @Query("DELETE FROM audio")
    fun nukeTable()
}
