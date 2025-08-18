package app.simple.felicity.repository.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
        tableName = "song_stats",
        indices = [
            Index(value = ["songId"]),
            Index(value = ["stableId"])
        ]
)
data class SongStat(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        val songId: Long,
        val stableId: String,
        val lastPlayed: Long = 0L,
        val playCount: Int = 0,
        val skipCount: Int = 0,
        val isFavorite: Boolean = false
) : Parcelable {
    override fun toString(): String {
        return "SongStat(id=$id, " +
                "songId=$songId, " +
                "stableId='$stableId', " +
                "lastPlayed=$lastPlayed, " +
                "playCount=$playCount, " +
                "skipCount=$skipCount, " +
                "isFavorite=$isFavorite)"
    }
}