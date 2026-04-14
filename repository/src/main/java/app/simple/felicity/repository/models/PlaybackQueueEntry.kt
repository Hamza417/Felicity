package app.simple.felicity.repository.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single slot in the persisted playback queue.
 *
 * Each row records which audio track sits at a given position in the saved queue.
 * The queue order is preserved by [queuePos] — restore it by querying
 * ORDER BY queuePos ASC.
 *
 * The old foreign-key link to audio.hash was removed when hash lost its unique
 * index (migration 10 → 11). FK enforcement was already disabled database-wide,
 * so this changes nothing at runtime. Stale queue entries (pointing at tracks
 * that no longer exist in the library) are cleaned up by the reconcile pass.
 *
 * @author Hamza417
 */
@Entity(
        tableName = "playback_queue",
        indices = [Index(value = ["audioHash"])]
)
data class PlaybackQueueEntry(
        @PrimaryKey val queuePos: Int,
        val audioHash: Long
)
