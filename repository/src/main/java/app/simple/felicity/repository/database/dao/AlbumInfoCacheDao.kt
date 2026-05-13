package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.MusicBrainzAlbumInfo

/**
 * Data Access Object for the [MusicBrainzAlbumInfo] cache table.
 *
 * Kept intentionally minimal — we only need to look up, write, and prune rows.
 */
@Dao
interface AlbumInfoCacheDao {

    /**
     * Returns the cached profile for the given [albumKey] (which is "albumName|artistName"),
     * or null if it has never been fetched before or was removed as stale.
     */
    @Query("SELECT * FROM album_info_cache WHERE album_key = :albumKey LIMIT 1")
    suspend fun getByAlbumKey(albumKey: String): MusicBrainzAlbumInfo?

    /**
     * Inserts or fully replaces the cache row for this album.
     * A fresh network fetch always wins over the old cached row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MusicBrainzAlbumInfo)

    /**
     * Deletes the cache row for [albumKey] so it gets re-fetched on next open.
     */
    @Query("DELETE FROM album_info_cache WHERE album_key = :albumKey")
    suspend fun deleteByAlbumKey(albumKey: String)

    /**
     * Removes all rows older than [cutoffMs] to keep the cache from growing indefinitely.
     */
    @Query("DELETE FROM album_info_cache WHERE fetched_at < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

