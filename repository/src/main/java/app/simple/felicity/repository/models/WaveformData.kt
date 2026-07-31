package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.simple.felicity.repository.models.WaveformData.Companion.fromFloatArray

/**
 * Stores the pre-computed waveform amplitude data for a single audio track.
 *
 * Extracting waveform samples from a raw audio file takes a noticeable amount
 * of time, especially on longer tracks. By persisting the result here, the app
 * can skip that work on every subsequent play of the same song and just read
 * from this table instead.
 *
 * The amplitudes are stored as a comma-separated list of floats so Room can
 * hold them in a plain TEXT column without needing any special type converter.
 * Each value is a normalized amplitude in [0.0, 1.0], with one entry per second
 * of audio (or whatever density {@code WaveformViewModel.BARS_PER_SECOND} dictates).
 *
 * Like [AudioStat] and [AlbumArtColors], there is intentionally no foreign key
 * back to the [Audio] table — the cached waveform survives library deletions
 * and re-associates automatically when the same file is rescanned.
 *
 * @author Hamza417
 */
@Entity(
        tableName = "waveform_data",
        indices = [Index(value = ["audioHash"], unique = true)]
)
data class WaveformData(

        @PrimaryKey
        val audioHash: Long,

        /**
         * All amplitude samples packed into one string, separated by commas.
         * Use [toFloatArray] and [fromFloatArray] helpers to convert back and forth.
         */
        @ColumnInfo(name = "amplitudes")
        val amplitudes: String
) {

    companion object {

        /**
         * Turns a [FloatArray] of normalized amplitudes into the comma-separated
         * string that gets stored in the database.
         */
        fun fromFloatArray(hash: Long, data: FloatArray): WaveformData {
            return WaveformData(
                    audioHash = hash,
                    amplitudes = data.joinToString(separator = ",")
            )
        }
    }

    /**
     * Parses the stored comma-separated string back into the [FloatArray] that
     * the waveform view can render. Returns an empty array if the stored value
     * is blank or can not be parsed.
     */
    fun toFloatArray(): FloatArray {
        if (amplitudes.isBlank()) return FloatArray(0)
        return amplitudes.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
    }
}

