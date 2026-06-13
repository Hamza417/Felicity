package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.SavedQueueEntry

/**
 * Data Access Object for the {@code saved_queue} table.
 *
 * <p>Manages five persistent queue slots (0–4) that the user can switch between.
 * Each queue is stored independently so switching queues never loses data —
 * the previous queue is archived and the new one is loaded into the active
 * {@code playback_queue} table for compatibility with the rest of the app.</p>
 *
 * @author Hamza417
 */
@Dao
interface SavedQueueDao {

    /**
     * Returns every saved entry for the given queue in position order.
     * Cascade-deleted entries are absent because the foreign key on audio.hash
     * prunes them automatically.
     */
    @Query("SELECT * FROM saved_queue WHERE queue_id = :queueId ORDER BY queue_pos ASC")
    suspend fun getQueue(queueId: Int): List<SavedQueueEntry>

    /**
     * Joins the saved queue against the audio table and returns full Audio rows
     * in the original queue order. Only tracks still present in the library are
     * returned — deleted tracks are silently omitted.
     */
    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN saved_queue sq ON a.hash = sq.audio_hash
        WHERE sq.queue_id = :queueId
        GROUP BY sq.queue_pos
        ORDER BY sq.queue_pos ASC
    """)
    suspend fun getQueuedAudios(queueId: Int): List<Audio>

    /**
     * Replaces all entries for the given queue with a fresh set.
     * Uses REPLACE so existing rows for the same queue are overwritten atomically.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SavedQueueEntry>)

    /**
     * Removes every entry for a specific queue, leaving it empty.
     */
    @Query("DELETE FROM saved_queue WHERE queue_id = :queueId")
    suspend fun clear(queueId: Int)

    /**
     * Removes every saved queue entry across all five slots.
     */
    @Query("DELETE FROM saved_queue")
    suspend fun clearAll()
}
