package app.simple.felicity.repository.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.simple.felicity.repository.models.EqualizerPreset.Companion.PRESET_TYPE_GRAPHIC
import app.simple.felicity.repository.models.EqualizerPreset.Companion.PRESET_TYPE_PARAMETRIC

/**
 * Represents a saved equalizer preset — a snapshot of either a 10-band graphic EQ curve
 * or a parametric EQ (PEQ) band configuration, stored so the user can switch between their
 * favorite sound profiles without re-dialing every control each time.
 *
 * Built-in presets (like "Bass Boost" or "Classical") are marked with [isBuiltIn] so
 * the app knows not to let the user delete them — nobody wants to lose the classics.
 *
 * Graphic presets store 10 band gains in [bandGainsRaw] (comma-separated floats).
 * Parametric presets store one entry per PEQ band in [peqBandsRaw], formatted as
 * "gain:q:freq|gain:q:freq|..." — each segment is a single filter with its gain (dB),
 * Q factor, and center frequency (Hz). [presetType] tells the app which format applies.
 *
 * @property id          Auto-assigned row id. Used as the stable key in the RecyclerView adapter.
 * @property name        Human-readable name shown in the preset list (e.g., "My Rock Mix").
 * @property bandGainsRaw Comma-separated string of 10 gain values in dB, e.g. "3.0,2.5,...".
 * @property preampDb    Pre-amplifier gain in dB applied before all band filters.
 * @property isBuiltIn   True for factory presets that cannot be deleted by the user.
 * @property dateCreated Epoch-millisecond timestamp for sorting user-created presets by age.
 * @property presetType  Either [PRESET_TYPE_GRAPHIC] or [PRESET_TYPE_PARAMETRIC].
 * @property peqBandsRaw Pipe-separated PEQ band data used when [presetType] is [PRESET_TYPE_PARAMETRIC].
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
         * This field is only meaningful when [presetType] is [PRESET_TYPE_GRAPHIC].
         */
        @ColumnInfo(name = "band_gains")
        val bandGainsRaw: String,

        @ColumnInfo(name = "preamp_db")
        val preampDb: Float = 0f,

        @ColumnInfo(name = "is_built_in")
        val isBuiltIn: Boolean = false,

        @ColumnInfo(name = "date_created")
        val dateCreated: Long = System.currentTimeMillis(),

        /**
         * Tells the app whether this is a 10-band graphic EQ preset or a parametric EQ preset.
         * Use [PRESET_TYPE_GRAPHIC] or [PRESET_TYPE_PARAMETRIC].
         */
        @ColumnInfo(name = "preset_type", defaultValue = PRESET_TYPE_GRAPHIC)
        val presetType: String = PRESET_TYPE_GRAPHIC,

        /**
         * Pipe-separated PEQ band data for parametric presets. Each band is stored as
         * "gain:q:freq" — three floats separated by colons, bands separated by pipes.
         * This field is null for graphic presets and not meaningful in that context.
         */
        @ColumnInfo(name = "peq_bands_raw")
        val peqBandsRaw: String? = null
) {

    /** Returns true when this is a parametric EQ preset rather than a graphic one. */
    fun isPeq(): Boolean = presetType == PRESET_TYPE_PARAMETRIC

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

    /**
     * Parses [peqBandsRaw] into a list of triples representing each parametric band.
     * Each triple holds (gainDb, qFactor, frequencyHz). Returns an empty list for
     * graphic presets or when the raw string is malformed.
     *
     * @return List of [Triple]<gainDb, qFactor, frequencyHz> for each PEQ band.
     */
    fun getPeqBands(): List<Triple<Float, Float, Float>> {
        val raw = peqBandsRaw ?: return emptyList()
        return raw.split("|").mapNotNull { segment ->
            val parts = segment.split(":")
            if (parts.size < 3) null
            else {
                val gain = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val q = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val freq = parts[2].toFloatOrNull() ?: return@mapNotNull null
                Triple(gain, q, freq)
            }
        }
    }

    companion object {

        const val PRESET_TYPE_GRAPHIC = "GRAPHIC"
        const val PRESET_TYPE_PARAMETRIC = "PARAMETRIC"

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
         * Serializes a list of PEQ bands into the pipe-separated "gain:q:freq|..." format
         * stored in [peqBandsRaw]. Each value is formatted to two decimal places.
         *
         * @param bands List of triples — each is (gainDb, qFactor, frequencyHz).
         * @return A compact pipe-separated string ready to store in the database.
         */
        fun peqBandsToRaw(bands: List<Triple<Float, Float, Float>>): String {
            return bands.joinToString("|") { (gain, q, freq) ->
                "%.2f:%.2f:%.2f".format(gain, q, freq)
            }
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
                    presetType = PRESET_TYPE_GRAPHIC,
                    dateCreated = System.currentTimeMillis()
            )
        }

        /**
         * Builds an [EqualizerPreset] from a list of PEQ bands. The [bandGainsRaw] field
         * is stored as all zeros since it is not used by parametric presets — only
         * [peqBandsRaw] matters here.
         *
         * @param name    Display name for the preset.
         * @param bands   List of (gainDb, qFactor, frequencyHz) triples, one per band.
         * @param preampDb Optional pre-amplifier gain (default 0 dB).
         */
        fun fromPeqBands(
                name: String,
                bands: List<Triple<Float, Float, Float>>,
                preampDb: Float = 0f
        ): EqualizerPreset {
            return EqualizerPreset(
                    name = name,
                    bandGainsRaw = gainsToRaw(FloatArray(10)),
                    preampDb = preampDb,
                    isBuiltIn = false,
                    presetType = PRESET_TYPE_PARAMETRIC,
                    peqBandsRaw = peqBandsToRaw(bands),
                    dateCreated = System.currentTimeMillis()
            )
        }
    }
}
