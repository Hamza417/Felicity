package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.simple.felicity.repository.models.EqualizerPreset
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the {@code equalizer_presets} table.
 *
 * <p>Returns live [Flow] streams wherever the caller needs real-time updates, so the
 * preset dialog stays in sync automatically when the user saves or deletes a preset
 * — no manual refresh required. It is like having a very attentive assistant who
 * updates the list the moment anything changes.</p>
 *
 * @author Hamza417
 */
@Dao
interface EqualizerPresetDao {

    /**
     * Returns all presets ordered so built-in ones come first (alphabetically),
     * then user-created ones sorted by newest first. This way factory presets
     * are always easy to find at the top.
     */
    @Query("""
        SELECT * FROM equalizer_presets
        ORDER BY is_built_in DESC, name COLLATE NOCASE ASC
    """)
    fun getAllPresetsFlow(): Flow<List<EqualizerPreset>>

    /**
     * Returns a single snapshot of all presets — useful when you need a one-time
     * read without subscribing to live updates.
     */
    @Query("SELECT * FROM equalizer_presets ORDER BY is_built_in DESC, name COLLATE NOCASE ASC")
    suspend fun getAllPresets(): List<EqualizerPreset>

    /**
     * Looks up a preset by its exact name. Useful for checking whether a built-in
     * preset already exists before trying to insert it again on startup.
     *
     * @param name The exact name to search for (case-insensitive).
     * @return The matching preset, or null if none exists.
     */
    @Query("SELECT * FROM equalizer_presets WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getPresetByName(name: String): EqualizerPreset?

    /**
     * Saves a new preset row. If a preset with the same primary key already exists
     * it is replaced — handy for re-seeding built-in presets without duplicating them.
     *
     * @param preset The preset to insert.
     * @return The auto-generated row id for the newly inserted preset.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqualizerPreset): Long

    /**
     * Saves a batch of presets in a single transaction. Used during database creation
     * to seed all the built-in presets at once without making 10 separate queries.
     *
     * @param presets The list of presets to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPresets(presets: List<EqualizerPreset>)

    /**
     * Updates an existing preset row matched by primary key. Useful for renaming
     * a user-created preset after the fact.
     *
     * @param preset The preset containing updated values.
     */
    @Update
    suspend fun updatePreset(preset: EqualizerPreset)

    /**
     * Permanently deletes a preset. Built-in presets can technically be deleted here,
     * but the UI should guard against that by checking [EqualizerPreset.isBuiltIn].
     *
     * @param preset The preset to delete.
     */
    @Delete
    suspend fun deletePreset(preset: EqualizerPreset)

    /**
     * Returns the total count of presets currently in the table.
     * Primarily used to decide whether built-in presets need to be seeded on first launch.
     */
    @Query("SELECT COUNT(*) FROM equalizer_presets WHERE is_built_in = 1")
    suspend fun getBuiltInPresetCount(): Int
}

