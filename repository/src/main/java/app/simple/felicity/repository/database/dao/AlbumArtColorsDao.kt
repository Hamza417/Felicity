package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.simple.felicity.repository.models.AlbumArtColors

/**
 * Handles all reads and writes for the [album_art_colors] table.
 *
 * The typical usage pattern is:
 * 1. When a song starts playing, call [getColorsByHash] to check if we already have
 *    cached colors for it.
 * 2. If the result is null, extract the palette from the bitmap and call [insertColors]
 *    to save it for next time.
 * 3. On subsequent plays of the same song, step 1 returns the cached row and the
 *    bitmap extraction step is skipped entirely.
 */
@Dao
interface AlbumArtColorsDao {

    /** Fetches the cached palette colors for a track by its content fingerprint. */
    @Query("SELECT * FROM album_art_colors WHERE audioHash = :audioHash LIMIT 1")
    suspend fun getColorsByHash(audioHash: Long): AlbumArtColors?

    /**
     * Saves a new palette row. If colors for this track were already stored,
     * they are replaced so the cache always holds the most recently extracted values.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColors(colors: AlbumArtColors)

    /** Overwrites an existing row with updated palette values. */
    @Update
    suspend fun updateColors(colors: AlbumArtColors)

    /** Removes the cached palette for a specific track. */
    @Query("DELETE FROM album_art_colors WHERE audioHash = :audioHash")
    suspend fun deleteColorsByHash(audioHash: Long)

    /** Clears the entire palette cache, useful when the user resets app data or rescans the library. */
    @Query("DELETE FROM album_art_colors")
    suspend fun deleteAllColors()
}

