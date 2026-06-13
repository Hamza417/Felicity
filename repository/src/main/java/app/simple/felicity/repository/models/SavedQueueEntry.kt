package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A single slot in one of the five saved playback queues.
 *
 * <p>Each row records which audio track sits at a given position inside a specific
 * saved queue identified by [queueId] (0–4). The queue order is preserved by
 * [queuePos] — restore it by querying ORDER BY queuePos ASC for the desired queue.
 * Multiple queues can coexist and the user can switch between them at any time
 * without losing the contents of any queue.</p>
 *
 * <p>Unlike {@link PlaybackQueueEntry} which holds the *currently active* queue,
 * this table is a persistent archive that survives across queue switches. The
 * active queue is still stored in {@code playback_queue} for compatibility with
 * the rest of the app — swapping queues means loading a saved queue into the
 * active slot.</p>
 *
 * @author Hamza417
 */
@Entity(
        tableName = "saved_queue",
        primaryKeys = ["queue_id", "queue_pos"],
        indices = [
            Index(value = ["queue_id"]),
            Index(value = ["audio_hash"])
        ]
)
data class SavedQueueEntry(
        @ColumnInfo(name = "queue_id")
        val queueId: Int,
        @ColumnInfo(name = "queue_pos")
        val queuePos: Int,
        @ColumnInfo(name = "audio_hash")
        val audioHash: Long
)
