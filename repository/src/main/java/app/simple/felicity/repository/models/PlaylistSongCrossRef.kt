package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table that associates [Audio] tracks with [Playlist] rows.
 *
 * Each row records the membership of one audio track in one playlist. The [position]
 * column preserves the user-defined ordering inside the playlist (e.g. after a
 * drag-and-drop reorder). When the playlist's sortOrder field is set to anything
 * other than -1 the UI ignores position and applies the selected sort field instead —
 * the position value is still kept up-to-date so manual ordering is never silently lost.
 *
 * The playlist cascade-delete foreign key is still active: deleting a playlist removes
 * all of its song membership rows automatically.
 *
 * The audio foreign key that used to reference audio.hash was removed in migration
 * 10 → 11 because hash is no longer unique in the audio table. FK enforcement was
 * already disabled database-wide, so the only thing that changes is that Room's schema
 * validator no longer rejects the build. Stale cross-ref rows (tracks removed from the
 * library) are cleaned up by the reconcile pass in the scanner.
 *
 * @author Hamza417
 */
@Entity(
        tableName = "playlist_song_cross_ref",
        primaryKeys = ["playlist_id", "audio_hash"],
        indices = [
            Index(value = ["playlist_id"]),
            Index(value = ["audio_hash"])
        ],
        foreignKeys = [
            ForeignKey(
                    entity = Playlist::class,
                    parentColumns = ["id"],
                    childColumns = ["playlist_id"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ]
)
data class PlaylistSongCrossRef(
        /** Foreign key referencing the owning [Playlist]. */
        @ColumnInfo(name = "playlist_id")
        val playlistId: Long,

        /** XXHash64 content fingerprint used as a logical reference to audio.hash. */
        @ColumnInfo(name = "audio_hash")
        val audioHash: Long,

        /** Zero-based position of the song within the playlist for manual ordering. */
        @ColumnInfo(name = "position", defaultValue = "0")
        val position: Int = 0
)
