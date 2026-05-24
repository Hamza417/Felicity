package app.simple.felicity.repository.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Represents a single bookmark entry for an audio track.
 *
 * Each bookmark is tied to an audio track via its content hash ([audioHash]) rather than a
 * database foreign key. This means bookmarks survive even if the track is removed from the
 * library — if the same file comes back later with the same hash, all its bookmarks are
 * automatically restored.
 *
 * The [timestampMs] field stores the exact playback position in milliseconds where the
 * bookmark was placed. The database enforces a uniqueness constraint on the combination
 * of [audioHash] and [timestampMs] so that duplicate bookmarks at the same second are
 * impossible even if the app tries to insert one.
 *
 * @author Hamza417
 */
@Parcelize
@Entity(
        tableName = "audio_bookmarks",
        indices = [
            Index(value = ["audioHash"]),
            Index(value = ["audioHash", "timestampMs"], unique = true)
        ]
)
data class AudioBookmark(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        /** The fingerprint of the audio track this bookmark belongs to. */
        val audioHash: Long,
        /** The playback position in milliseconds where the bookmark was placed. */
        val timestampMs: Long,
        /** When the bookmark was created, as a Unix timestamp in milliseconds. */
        val createdAt: Long = System.currentTimeMillis()
) : Parcelable

