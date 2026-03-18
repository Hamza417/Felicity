package app.simple.felicity.repository.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Tracks per-song playback statistics stored in the {@code song_stats} table.
 *
 * <p>The {@code audioHash} field is a non-cascade foreign key that references the
 * {@code hash} column of the {@code audio} table. Because no cascade delete is set,
 * stats survive even when the corresponding audio row is removed from the library.
 * The {@code audioHash} value is the XXHash64 content fingerprint produced during
 * library scanning and is guaranteed to be unique per physical audio file.</p>
 *
 * @author Hamza417
 */
@Parcelize
@Entity(
        tableName = "song_stats",
        indices = [Index(value = ["audioHash"])],
        foreignKeys = [
            ForeignKey(
                    entity = Audio::class,
                    parentColumns = ["hash"],
                    childColumns = ["audioHash"],
                    onDelete = ForeignKey.NO_ACTION,
                    onUpdate = ForeignKey.CASCADE
            )
        ]
)
data class AudioStat(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        val audioHash: Long,
        val lastPlayed: Long = 0L,
        val playCount: Int = 0,
        val skipCount: Int = 0
) : Parcelable {
    override fun toString(): String {
        return "AudioStat(id=$id, " +
                "audioHash=$audioHash, " +
                "lastPlayed=$lastPlayed, " +
                "playCount=$playCount, " +
                "skipCount=$skipCount)"
    }
}