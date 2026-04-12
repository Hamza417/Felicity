package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a saved equalizer preset — basically a snapshot of all 10 band gains
 * plus the preamp level, stored so the user can recall their favorite sound profiles
 * without having to manually tweak every slider again. How convenient!
 *
 * Built-in presets (like "Bass Boost" or "Classical") are marked with [isBuiltIn] so
 * the app knows not to let the user delete them — nobody wants to lose the classics.
 *
 * Band gains are stored as a comma-separated string in [bandGainsRaw] and converted
 * to and from [FloatArray] by the companion helper functions. Room isn't great with
 * float arrays directly, so we do a little string dance to make it work smoothly.
 *
 * @property id          Auto-assigned row id. Used as the stable key in the RecyclerView adapter.
 * @property name        Human-readable name shown in the preset list (e.g., "My Rock Mix").
 * @property bandGainsRaw Comma-separated string of 10 gain values in dB, e.g. "3.0,2.5,...".
 * @property preampDb    Pre-amplifier gain in dB applied before all band filters.
 * @property isBuiltIn   True for factory presets that cannot be deleted by the user.
 * @property dateCreated Epoch-millisecond timestamp for sorting user-created presets by age.
 *
 * @author Hamza417
 */
@Entity(tableName = "equalizer_presets")
data class EqualizerPreset(

        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "id")
        val id: Long = 0,

        @ColumnInfo(name = "name")
        val name: String,

        /**
         * The 10 band gains as a comma-separated string — not the prettiest format, but it
         * works perfectly without needing a custom type converter or JSON library.
         * Each value is a float in dB, e.g. "3.0,-1.5,0.0,...".
         */
        @ColumnInfo(name = "band_gains")
        val bandGainsRaw: String,

        @ColumnInfo(name = "preamp_db")
        val preampDb: Float = 0f,

        @ColumnInfo(name = "is_built_in")
        val isBuiltIn: Boolean = false,

        @ColumnInfo(name = "date_created")
        val dateCreated: Long = System.currentTimeMillis()
) {

    /**
     * Parses [bandGainsRaw] back into a proper [FloatArray] of 10 gains.
     * If the raw string is malformed or shorter than 10 values, missing entries default to 0.0 dB.
     *
     * @return A [FloatArray] of exactly 10 band gain values in dB.
     */
    fun getBandGains(): FloatArray {
        val parts = bandGainsRaw.split(",")
        return FloatArray(10) { i ->
            parts.getOrNull(i)?.trim()?.toFloatOrNull() ?: 0f
        }
    }

    companion object {

        /**
         * Converts a [FloatArray] of 10 band gains into the comma-separated string format
         * expected by [bandGainsRaw]. This is the reverse of [getBandGains].
         *
         * @param gains Array of 10 gain values in dB.
         * @return A compact comma-separated string like "3.0,-1.5,0.0,...".
         */
        fun gainsToRaw(gains: FloatArray): String {
            return gains.joinToString(",") { "%.2f".format(it) }
        }

        /**
         * Shortcut to build an [EqualizerPreset] with a plain [FloatArray] instead of
         * manually converting to [bandGainsRaw] every time. Use this in factory-preset
         * definitions to keep things readable.
         *
         * @param name      Display name for the preset.
         * @param gains     Array of 10 EQ band gains in dB.
         * @param preampDb  Optional pre-amplifier gain (default 0 dB).
         * @param isBuiltIn Whether this is a factory preset (default false).
         */
        fun fromGains(
                name: String,
                gains: FloatArray,
                preampDb: Float = 0f,
                isBuiltIn: Boolean = false
        ): EqualizerPreset {
            return EqualizerPreset(
                    name = name,
                    bandGainsRaw = gainsToRaw(gains),
                    preampDb = preampDb,
                    isBuiltIn = isBuiltIn,
                    dateCreated = System.currentTimeMillis()
            )
        }
    }
}
