package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached version of the MusicBrainz artist profile stored in Room so we don't
 * have to hit the network every time the user opens an artist page.
 *
 * The [artistName] is the primary key because it's what we search by — MBIDs are
 * resolved during the fetch and stored alongside so future lookups that have an
 * MBID can skip the search step entirely.
 *
 * [fetchedAt] is a Unix timestamp (milliseconds) we use to decide when the cache
 * is stale and should be refreshed.
 *
 * Tags are stored as a single pipe-separated string (e.g. "electronic|pop|edm")
 * to avoid needing a separate join table for such a small list.
 *
 * @author Hamza417
 */
@Entity(tableName = "artist_info_cache")
data class MusicBrainzArtistInfo(
        @PrimaryKey
        @ColumnInfo(name = "artist_name")
        val artistName: String,

        @ColumnInfo(name = "mbid")
        val mbid: String?,

        @ColumnInfo(name = "disambiguation")
        val disambiguation: String?,

        @ColumnInfo(name = "type")
        val type: String?,

        @ColumnInfo(name = "country")
        val country: String?,

        @ColumnInfo(name = "begin_year")
        val beginYear: String?,

        @ColumnInfo(name = "end_year")
        val endYear: String?,

        @ColumnInfo(name = "ended")
        val ended: Boolean,

        /** Pipe-separated list of genre/style tags, e.g. "electronic|pop|edm". */
        @ColumnInfo(name = "tags")
        val tags: String,

        @ColumnInfo(name = "bio")
        val bio: String?,

        @ColumnInfo(name = "wikipedia_url")
        val wikipediaUrl: String?,

        /** When this row was written, in System.currentTimeMillis() format. */
        @ColumnInfo(name = "fetched_at")
        val fetchedAt: Long
)

