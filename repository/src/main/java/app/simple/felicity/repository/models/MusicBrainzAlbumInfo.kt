package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached version of the MusicBrainz release profile stored in Room so we don't
 * hit the network every time the user opens an album page.
 *
 * The [albumKey] is a composite of album name and artist name joined with a pipe
 * (e.g. "Meteora|Linkin Park") and serves as the primary key, because MusicBrainz
 * releases are best identified by both title and artist together.
 *
 * Tags are stored as a pipe-separated string (e.g. "alternative metal|rock")
 * and labels the same way, to keep the schema flat without a join table.
 *
 * [fetchedAt] is a Unix timestamp (milliseconds) used to decide when the cache
 * entry is stale enough to warrant a fresh network call.
 *
 * @author Hamza417
 */
@Entity(tableName = "album_info_cache")
data class MusicBrainzAlbumInfo(
        @PrimaryKey
        @ColumnInfo(name = "album_key")
        val albumKey: String,

        @ColumnInfo(name = "mbid")
        val mbid: String?,

        @ColumnInfo(name = "disambiguation")
        val disambiguation: String?,

        /** The release date as returned by MusicBrainz, e.g. "2003-03-25" or just "2003". */
        @ColumnInfo(name = "release_date")
        val releaseDate: String?,

        @ColumnInfo(name = "country")
        val country: String?,

        /** Release status from MusicBrainz, e.g. "Official", "Bootleg", "Promotional". */
        @ColumnInfo(name = "status")
        val status: String?,

        /** Pipe-separated list of genre/style tags, e.g. "alternative metal|nu-metal|rock". */
        @ColumnInfo(name = "tags")
        val tags: String,

        /** Pipe-separated list of record label names. */
        @ColumnInfo(name = "labels")
        val labels: String,

        @ColumnInfo(name = "bio")
        val bio: String?,

        @ColumnInfo(name = "wikipedia_url")
        val wikipediaUrl: String?,

        /** When this row was written, in System.currentTimeMillis() format. */
        @ColumnInfo(name = "fetched_at")
        val fetchedAt: Long
)

