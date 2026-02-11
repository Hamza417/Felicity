package app.simple.felicity.repository.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackState(
        @PrimaryKey val id: Int = 1,          // always overwrite row 1
        val queue: String,                    // JSON of track IDs
        val index: Int,
        val position: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
        val updatedAt: Long
)