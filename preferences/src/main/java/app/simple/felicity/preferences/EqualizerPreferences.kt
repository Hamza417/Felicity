package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.EqualizerPreferences.EQ_BAND_KEY_PREFIX

/**
 * Manages all equalizer-related audio processing preferences for the Felicity playback engine.
 *
 * Covers stereo balance, stereo widening, tape saturation drive, karaoke mode, night mode,
 * and the 10-band graphic equalizer band gains. Every value is persisted here and observed
 * by the player service to immediately update the live audio processor chain.
 *
 * @author Hamza417
 */
object EqualizerPreferences {
    /**
     * Stereo pan/balance stored as a float in [-1 .. 1].
     * -1 = full left, 0 = center (default), +1 = full right.
     * Applied via constant-power panning in the audio engine.
     */
    const val BALANCE = "player_balance"

    /**
     * Stereo widening width stored as a float in [0 .. 2].
     * 0.0 = full mono, 1.0 = natural stereo (default), 2.0 = maximum widening.
     * Applied via mid/side matrix in the audio engine.
     */
    const val STEREO_WIDTH = "player_stereo_width"

    /**
     * Tape saturation drive stored as a float in [0 .. 4].
     * 0.0 = clean bypass (default), 4.0 = maximum drive.
     * Applied via algebraic soft-clip transfer function in the audio engine.
     */
    const val TAPE_SATURATION_DRIVE = "player_tape_saturation_drive"

    /** Boolean flag for the karaoke (center-channel removal) processor. */
    const val KARAOKE_MODE_ENABLED = "equalizer_karaoke_mode_enabled"

    /** Boolean flag for the night mode dynamic compressor/limiter processor. */
    const val NIGHT_MODE_ENABLED = "equalizer_night_mode_enabled"

    /**
     * Boolean flag controlling whether the 10-band graphic equalizer is active.
     * Defaults to true so that saved band adjustments are always applied on startup.
     */
    const val EQ_ENABLED = "eq_enabled"

    /**
     * Shared prefix for all per-band gain keys. The full key for band N is
     * [EQ_BAND_KEY_PREFIX] + N (e.g., "eq_band_3").
     * Gains are stored as floats in dB in the range [-15 .. +15].
     */
    const val EQ_BAND_KEY_PREFIX = "eq_band_"
    fun setBalance(pan: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(BALANCE, pan.coerceIn(-1f, 1f)) }
    }

    fun getBalance(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(BALANCE, 0f)
    }

    fun setStereoWidth(width: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(STEREO_WIDTH, width.coerceIn(0f, 2f)) }
    }

    fun getStereoWidth(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(STEREO_WIDTH, 1f)
    }

    fun setTapeSaturationDrive(drive: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(TAPE_SATURATION_DRIVE, drive.coerceIn(0f, 4f))
        }
    }

    fun getTapeSaturationDrive(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(TAPE_SATURATION_DRIVE, 0f)
    }

    fun setKaraokeModeEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(KARAOKE_MODE_ENABLED, enabled)
        }
    }

    fun isKaraokeModeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(KARAOKE_MODE_ENABLED, false)
    }

    fun setNightModeEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(NIGHT_MODE_ENABLED, enabled)
        }
    }

    fun isNightModeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(NIGHT_MODE_ENABLED, false)
    }

    // -------------------------------------------------------------------------
    // 10-Band EQ
    // -------------------------------------------------------------------------
    /**
     * Persists whether the 10-band graphic equalizer is enabled.
     *
     * @param enabled True to pass audio through the EQ bands, false to bypass.
     */
    fun setEqEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(EQ_ENABLED, enabled) }
    }

    /**
     * Returns whether the 10-band graphic equalizer is currently enabled.
     * Defaults to true so saved band adjustments are always audible on startup.
     */
    fun isEqEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(EQ_ENABLED, true)
    }

    /**
     * Persists the gain for a single EQ band.
     *
     * @param band   Zero-based band index in [0 .. 9].
     * @param gainDb Gain in dB, clamped to [-15 .. +15].
     */
    fun setBandGain(band: Int, gainDb: Float) {
        if (band !in 0..9) return
        SharedPreferences.getSharedPreferences().edit {
            putFloat(EQ_BAND_KEY_PREFIX + band, gainDb.coerceIn(-15f, 15f))
        }
    }

    /**
     * Returns the persisted gain for [band] in dB.
     * Defaults to 0.0 (flat) when no value has been saved yet.
     *
     * @param band Zero-based band index in [0 .. 9].
     */
    fun getBandGain(band: Int): Float {
        if (band !in 0..9) return 0f
        return SharedPreferences.getSharedPreferences().getFloat(EQ_BAND_KEY_PREFIX + band, 0f)
    }

    /**
     * Returns all 10 band gains as a [FloatArray] ordered from 31 Hz (index 0) to 16 kHz (index 9).
     * Any band that has not been explicitly set defaults to 0.0 dB (flat).
     */
    fun getAllBandGains(): FloatArray {
        return FloatArray(10) { i -> getBandGain(i) }
    }

    /**
     * Persists all 10 band gains in a single batch edit for efficiency.
     *
     * @param gains Array of 10 gain values in dB. Values beyond index 9 are ignored;
     *              missing entries default to 0.0 dB.
     */
    fun setAllBandGains(gains: FloatArray) {
        SharedPreferences.getSharedPreferences().edit {
            for (i in 0..9) {
                putFloat(EQ_BAND_KEY_PREFIX + i, if (i < gains.size) gains[i].coerceIn(-15f, 15f) else 0f)
            }
        }
    }
}
