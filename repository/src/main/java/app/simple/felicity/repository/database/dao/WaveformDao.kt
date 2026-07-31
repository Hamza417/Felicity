package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.WaveformData

/**
 * Handles reads and writes for the {@code waveform_data} table.
 *
 * The intended usage pattern follows the "DB first" approach:
 * 1. When a track is about to be displayed, call [getWaveformByHash] with its fingerprint.
 * 2. If the result is non-null, hand the stored amplitudes straight to the waveform view —
 *    no audio file decoding needed.
 * 3. If the result is null, extract the amplitude data from the file, push it to the
 *    view, and then call [insertWaveform] so the next play of the same track hits the DB.
 */
@Dao
interface WaveformDao {

    /** Returns the cached waveform row for the given track hash, or null if it hasn't been stored yet. */
    @Query("SELECT * FROM waveform_data WHERE audioHash = :audioHash LIMIT 1")
    suspend fun getWaveformByHash(audioHash: Long): WaveformData?

    /**
     * Saves a new waveform row. If one already exists for this track it is replaced,
     * so re-extracting a track always results in the freshest data being kept.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaveform(data: WaveformData)

    /** Removes the stored waveform for a specific track, forcing a fresh extraction on next play. */
    @Query("DELETE FROM waveform_data WHERE audioHash = :audioHash")
    suspend fun deleteWaveformByHash(audioHash: Long)

    /** Drops every cached waveform, useful when the user triggers a full library rescan. */
    @Query("DELETE FROM waveform_data")
    suspend fun deleteAllWaveforms()
}

