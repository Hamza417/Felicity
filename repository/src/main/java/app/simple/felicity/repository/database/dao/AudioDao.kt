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

    // Get recent audio with filtering
    @Query("SELECT * FROM audio WHERE is_available = 1 AND duration >= :minDuration AND size >= :minSize ORDER BY date_added DESC LIMIT 25")
    fun getFilteredRecentAudio(minDuration: Long, minSize: Long): Flow<MutableList<Audio>>

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

    @Query("SELECT id FROM audio WHERE path = :path AND is_available = 1")
    fun getAudioIdByPath(path: String): Long

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
     * Insert multiple [Audio] items in a batch
     * into the table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
