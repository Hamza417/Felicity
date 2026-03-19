package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackQueueEntry

/**
 * Data Access Object for the {@code playback_queue} table.
 *
 * <p>Each row stores one queue slot. Rows are automatically removed by SQLite
 * whenever the referenced {@code audio.hash} is deleted (cascade-delete FK).</p>
 *
 * @author Hamza417
 */
@Dao
interface PlaybackQueueDao {

    /**
     * Returns every queued entry in the order they were enqueued.
     * Cascade-deleted entries are absent, so the result may have fewer items than
     * the original queue if audio tracks were removed from the library.
     */
    @Query("SELECT * FROM playback_queue ORDER BY queuePos ASC")
    suspend fun getQueue(): List<PlaybackQueueEntry>

    /**
     * Joins the queue against the audio table and returns the full Audio rows in
     * queue order. Only available tracks are returned; deleted songs are already
     * gone due to cascade deletion.
     */
    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN playback_queue pq ON a.hash = pq.audioHash
        ORDER BY pq.queuePos ASC
    """)
    suspend fun getQueuedAudios(): List<Audio>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PlaybackQueueEntry>)

    @Query("DELETE FROM playback_queue")
    suspend fun clear()
}

