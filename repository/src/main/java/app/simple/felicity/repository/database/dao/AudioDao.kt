package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio ORDER BY title COLLATE NOCASE ASC")
    fun getAllAudio(): Flow<MutableList<Audio>>

    // Get unique artists
    @Query("SELECT * FROM audio GROUP BY artist ORDER BY artist COLLATE NOCASE ASC")
    fun getAllArtists(): Flow<MutableList<Audio>>

    // Get unique albums
    @Query("SELECT * FROM audio GROUP BY album ORDER BY album COLLATE NOCASE ASC")
    fun getAllAlbums(): Flow<MutableList<Audio>>

    // Get recent audio
    @Query("SELECT * FROM audio ORDER BY date_added DESC LIMIT 25")
    fun getRecentAudio(): Flow<MutableList<Audio>>

    // get all audio files by artist name in ascending order
    @Query("SELECT * FROM audio WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getAudioByArtist(artist: String): Flow<MutableList<Audio>>

    @Query("SELECT id FROM audio WHERE path = :path")
    fun getAudioIdByPath(path: String): Long

    @RawQuery
    fun getQueriedData(query: SupportSQLiteQuery): MutableList<Audio>

    /**
     * Delete a [Audio] item
     * from the table
     */
    @Delete
    suspend fun delete(audio: Audio)

    /**
     * Insert [Audio] item
     * into the table
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(audio: Audio)

    /**
     * Delete the entire table
     */
    @Query("DELETE FROM audio")
    fun nukeTable()
}
