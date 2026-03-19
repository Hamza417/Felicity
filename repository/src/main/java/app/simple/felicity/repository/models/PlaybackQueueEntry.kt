package app.simple.felicity.repository.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single slot in the persisted playback queue.
 *
 * <p>Each row records the position of one track within the saved queue. The
 * {@code audioHash} field references {@code audio.hash} via a cascade-delete foreign
 * key, so rows are automatically purged whenever the corresponding audio track is
 * removed from the library — no stale references ever accumulate.</p>
 *
 * <p>Queue order is preserved by {@code queuePos}: restore by querying
 * {@code ORDER BY queuePos ASC}.</p>
 *
 * @author Hamza417
 */
@Entity(
        tableName = "playback_queue",
        indices = [Index(value = ["audioHash"])],
        foreignKeys = [
            ForeignKey(
                    entity = Audio::class,
                    parentColumns = ["hash"],
                    childColumns = ["audioHash"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ]
)
data class PlaybackQueueEntry(
        @PrimaryKey val queuePos: Int,
        val audioHash: Long
)

