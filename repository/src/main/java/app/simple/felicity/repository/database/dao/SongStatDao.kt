package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for {@link AudioStat} records stored in the {@code song_stats} table.
 *
 * <p>Provides reactive flows for recently played and most played songs by joining
 * {@code song_stats} with the {@code audio} table on the shared {@code hash} /
 * {@code audioHash} column.</p>
 */
@Dao
interface SongStatDao {

    @Query("SELECT * FROM song_stats WHERE audioHash = :audioHash LIMIT 1")
    suspend fun getStatByHash(audioHash: Long): AudioStat?

    /**
     * Returns up to 50 available songs ordered by their most recent play time, newest first.
     */
    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN song_stats ss ON a.hash = ss.audioHash
        WHERE a.is_available = 1 AND ss.lastPlayed > 0
        ORDER BY ss.lastPlayed DESC
        LIMIT 50
    """)
    fun getRecentlyPlayedAudio(): Flow<List<Audio>>

    /**
     * Returns up to 50 available songs ordered by their total play count, highest first.
     */
    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN song_stats ss ON a.hash = ss.audioHash
        WHERE a.is_available = 1 AND ss.playCount > 0
        ORDER BY ss.playCount DESC
        LIMIT 50
    """)
    fun getMostPlayedAudio(): Flow<List<Audio>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(audioStat: AudioStat)

    @Update
    suspend fun updateStat(audioStat: AudioStat)

    @Delete
    suspend fun deleteStat(audioStat: AudioStat)
}