package app.simple.felicity.repository.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Tracks per-song playback statistics stored in the {@code song_stats} table.
 *
 * <p>The {@code audioHash} field is the XXHash64 content fingerprint produced during library
 * scanning and logically identifies the audio track. There is intentionally no database-level
 * foreign key pointing at {@code audio.hash}: a hard FK with {@code ON DELETE NO ACTION} would
 * prevent deleting audio rows that have statistics, while the desired behavior is that stats
 * survive even after the corresponding track is removed from the library. If the same file is
 * re-added later, the same fingerprint is computed and the historical stats are automatically
 * associated again.</p>
 *
 * @author Hamza417
 */
@Parcelize
@Entity(
        tableName = "song_stats",
        indices = [Index(value = ["audioHash"])]
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