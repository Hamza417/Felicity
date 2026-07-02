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
        val audioHash: Long,
        /**
         * The song index within this queue that was playing when the queue was
         * last archived. Stored on every row of the same queue so it survives
         * even when individual songs are cascade-deleted — the first remaining
         * row still carries the correct restore position.
         */
        @ColumnInfo(name = "last_position", defaultValue = "0")
        val lastPosition: Int = 0,
        /**
         * The seek offset in milliseconds within the song at [lastPosition] when
         * the queue was last archived. Same redundancy strategy as [lastPosition].
         */
        @ColumnInfo(name = "last_seek", defaultValue = "0")
        val lastSeek: Long = 0L
)
