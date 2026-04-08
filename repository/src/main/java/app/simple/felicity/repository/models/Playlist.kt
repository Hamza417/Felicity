package app.simple.felicity.repository.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Represents a user-created playlist stored in the {@code playlists} table.
 *
 * <p>The {@code sortOrder} and {@code sortStyle} fields capture the user's last chosen
 * in-playlist sort configuration per playlist, mirroring the approach used by the Songs
 * and Favorites panels. A value of {@code -1} for {@code sortOrder} signals "use the
 * natural (manual) insertion order" so the playlist preserves the drag-and-drop sequence
 * the user arranged via the {@code position} column in {@link PlaylistSongCrossRef}.</p>
 *
 * <p>The {@code artworkPath} field holds an optional path to a user-selected cover image.
 * When {@code null} the UI should fall back to a mosaic of the first few songs' album art.</p>
 *
 * @author Hamza417
 */
@Parcelize
@Entity(tableName = "playlists")
data class Playlist(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "id")
        val id: Long = 0L,

        /** Human-readable display name of the playlist. */
        @ColumnInfo(name = "name")
        val name: String,

        /** Optional free-text description shown below the playlist name in the UI. */
        @ColumnInfo(name = "description")
        val description: String? = null,

        /** Epoch-millisecond timestamp when the playlist was first created. */
        @ColumnInfo(name = "date_created")
        val dateCreated: Long = System.currentTimeMillis(),

        /** Epoch-millisecond timestamp of the most recent structural change (rename, add/remove song, reorder). */
        @ColumnInfo(name = "date_modified")
        val dateModified: Long = System.currentTimeMillis(),

        /** Epoch-millisecond timestamp of the last time the playlist was opened or played. */
        @ColumnInfo(name = "last_accessed", defaultValue = "0")
        val lastAccessed: Long = 0L,

        /**
         * Optional absolute file-system path to a custom cover art image chosen by the user.
         * When {@code null} the UI falls back to a mosaic derived from the member songs' album art.
         */
        @ColumnInfo(name = "artwork_path")
        val artworkPath: String? = null,

        /**
         * The sort-field constant applied to songs inside this playlist.
         * Maps to a {@code BY_*} value from
         * {@link app.simple.felicity.constants.CommonPreferencesConstants}, or {@code -1} to
         * preserve the manual insertion order stored in
         * {@link PlaylistSongCrossRef#position}.
         */
        @ColumnInfo(name = "sort_order", defaultValue = "-1")
        val sortOrder: Int = -1,

        /**
         * Sort direction constant ({@code ASCENDING} or {@code DESCENDING}) from
         * {@link app.simple.felicity.constants.CommonPreferencesConstants}.
         * Only meaningful when {@code sortOrder} is not {@code -1}.
         */
        @ColumnInfo(name = "sort_style", defaultValue = "0")
        val sortStyle: Int = 0,

        /**
         * Whether the playlist should shuffle its tracks on playback.
         * Stored per-playlist so each playlist remembers its own shuffle preference.
         */
        @ColumnInfo(name = "is_shuffled", defaultValue = "0")
        val isShuffled: Boolean = false,

        /**
         * Whether this playlist is pinned to the top of the playlist list
         * regardless of the active list-level sort order.
         */
        @ColumnInfo(name = "is_pinned", defaultValue = "0")
        val isPinned: Boolean = false
) : Parcelable

