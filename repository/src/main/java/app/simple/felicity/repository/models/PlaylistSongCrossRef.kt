package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table that associates {@link Audio} tracks with {@link Playlist} rows.
 *
 * <p>Each row records the membership of one audio track in one playlist. The
 * {@code position} column preserves the user-defined ordering inside the playlist
 * (e.g. after a drag-and-drop reorder). When the playlist's {@code sortOrder} field
 * is set to anything other than {@code -1} the UI ignores {@code position} and applies
 * the selected sort field instead. The {@code position} value is still kept up-to-date
 * so that the manual ordering is never silently discarded when the user switches back.</p>
 *
 * <p>Two cascade-delete foreign keys are declared:</p>
 * <ul>
 *   <li>{@code playlist_id} → {@code playlists.id}: deleting a playlist removes all of
 *       its song membership rows automatically.</li>
 *   <li>{@code audio_hash} → {@code audio.hash}: removing a track from the library
 *       automatically removes it from every playlist — identical to the behavior of
 *       {@link PlaybackQueueEntry}.</li>
 * </ul>
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
            ),
            ForeignKey(
                    entity = Audio::class,
                    parentColumns = ["hash"],
                    childColumns = ["audio_hash"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ]
)
data class PlaylistSongCrossRef(
        /** Foreign key referencing the owning {@link Playlist}. */
        @ColumnInfo(name = "playlist_id")
        val playlistId: Long,

        /** XXHash64 content fingerprint referencing {@code audio.hash}. */
        @ColumnInfo(name = "audio_hash")
        val audioHash: Long,

        /** Zero-based position of the song within the playlist for manual ordering. */
        @ColumnInfo(name = "position", defaultValue = "0")
        val position: Int = 0
)

