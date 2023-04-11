package app.simple.felicity.database.dao

import androidx.room.*
import app.simple.felicity.models.Audio

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllAudio(): MutableList<Audio>

    // Get unique artists
    @Query("SELECT * FROM audio GROUP BY artist ORDER BY artist COLLATE NOCASE ASC")
    suspend fun getAllArtists(): MutableList<Audio>

    // Get unique albums
    @Query("SELECT * FROM audio GROUP BY album ORDER BY album COLLATE NOCASE ASC")
    suspend fun getAllAlbums(): MutableList<Audio>

    // Get recent audio
    @Query("SELECT * FROM audio ORDER BY date_added DESC LIMIT 25")
    suspend fun getRecentAudio(): MutableList<Audio>

    // get all audio files by artist name in ascending order
    @Query("SELECT * FROM audio WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAudioByArtist(artist: String): MutableList<Audio>

    /**
     * Delete a [Audio] item
     * from the table
     */
    @Delete
    suspend fun deleteAudio(audio: Audio)

    /**
     * Insert [Audio] item
     * into the table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(audio: Audio)

    /**
     * Delete the entire table
     */
    @Query("DELETE FROM audio")
    fun nukeTable()
}