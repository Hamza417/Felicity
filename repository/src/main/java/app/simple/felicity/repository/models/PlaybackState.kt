package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single-row snapshot of the last known playback state.
 *
 * <p>The queue itself is stored separately in {@code playback_queue} (see
 * {@link PlaybackQueueEntry}), which carries a cascade-delete foreign key so that
 * removing an audio track automatically prunes it from the saved queue. This row
 * only holds scalar state: the current position index, seek offset, repeat mode, and
 * the hash of the song that was active — the hash lets the restore logic find the
 * right index even after cascade deletions shifted the queue positions.</p>
 *
 * <p>[activeQueueId] tracks which of the five saved queues (0–4) was active when
 * the state was last persisted. On app launch the queue matching this ID is
 * restored alongside the scalar playback state so the user picks up exactly where
 * they left off.</p>
 *
 * @author Hamza417
 */
@Entity(tableName = "playback_state")
data class PlaybackState(
        @PrimaryKey val id: Int = 1,
        @ColumnInfo(name = "current_index") val index: Int = 0,
        @ColumnInfo(name = "position_ms") val position: Long = 0L,
        val shuffle: Boolean = false,
        val repeatMode: Int = 0,
        val updatedAt: Long = 0L,
        /** fingerprint of the song that was active when state was saved. */
        @ColumnInfo(name = "current_hash") val currentHash: Long = 0L,
        /** Which of the five saved queues (0–4) was active when state was persisted. */
        @ColumnInfo(name = "active_queue_id", defaultValue = "0")
        val activeQueueId: Int = 0
)

