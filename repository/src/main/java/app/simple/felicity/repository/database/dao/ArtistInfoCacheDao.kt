package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.MusicBrainzArtistInfo

/**
 * Data Access Object for the [MusicBrainzArtistInfo] cache table.
 *
 * We keep this intentionally small — the only operations needed are:
 * look up by artist name, write/replace a row, and delete stale rows.
 */
@Dao
interface ArtistInfoCacheDao {

    /**
     * Returns the cached profile for [artistName], or null if it has never
     * been fetched before or was deleted as stale.
     */
    @Query("SELECT * FROM artist_info_cache WHERE artist_name = :artistName LIMIT 1")
    suspend fun getByArtistName(artistName: String): MusicBrainzArtistInfo?

    /**
     * Inserts or fully replaces the cache row for this artist.
     * Replace strategy means a new network fetch always wins over the old row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MusicBrainzArtistInfo)

    /**
     * Deletes the cache row for [artistName] so it gets re-fetched next time.
     */
    @Query("DELETE FROM artist_info_cache WHERE artist_name = :artistName")
    suspend fun deleteByArtistName(artistName: String)

    /**
     * Removes all rows whose [MusicBrainzArtistInfo.fetchedAt] timestamp is
     * older than [cutoffMs]. Useful for periodic cache pruning.
     */
    @Query("DELETE FROM artist_info_cache WHERE fetched_at < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

