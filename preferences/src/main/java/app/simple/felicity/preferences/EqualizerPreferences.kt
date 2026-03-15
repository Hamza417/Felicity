package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

/**
 * Manages all equalizer-related audio processing preferences for the Felicity playback engine.
 *
 * Covers stereo balance, stereo widening, tape saturation drive, karaoke mode, and night mode.
 * All knob and toggle states are persisted here and observed by the player service to instantly
 * update the live audio processor chain.
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
     * 0.0 = full mono, 1.0 = natural stereo (default, no processing),
     * 2.0 = maximum widening.
     * Applied via mid/side matrix in the audio engine.
     */
    const val STEREO_WIDTH = "player_stereo_width"

    /**
     * Tape saturation drive stored as a float in [0 .. 4].
     * 0.0 = clean bypass (default, no processing), 1.0 = subtle warmth,
     * 2.0 = punchy saturation, 4.0 = maximum drive.
     * Applied via algebraic soft-clip transfer function in the audio engine.
     */
    const val TAPE_SATURATION_DRIVE = "player_tape_saturation_drive"

    /**
     * Boolean flag for the karaoke (center-channel removal) processor.
     * When true the processor subtracts R from L to erase center-panned vocals.
     * Requires a stereo source; mono sources are passed through unchanged.
     */
    const val KARAOKE_MODE_ENABLED = "equalizer_karaoke_mode_enabled"

    /**
     * Boolean flag for the night mode dynamic compressor/limiter processor.
     * When true a soft compressor with makeup gain is applied to keep loud peaks
     * quiet and boost soft passages, reducing the listening dynamic range.
     */
    const val NIGHT_MODE_ENABLED = "equalizer_night_mode_enabled"

    /**
     * Persists [pan] in [-1f .. 1f].
     * -1 = full left, 0 = center, +1 = full right.
     */
    fun setBalance(pan: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(BALANCE, pan.coerceIn(-1f, 1f))
        }
    }

    /**
     * Returns the persisted pan value, defaulting to 0 (center).
     */
    fun getBalance(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(BALANCE, 0f)
    }

    /**
     * Persists [width] in [0f .. 2f].
     * 0.0 = mono, 1.0 = natural stereo (no change), 2.0 = maximum widening.
     */
    fun setStereoWidth(width: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(STEREO_WIDTH, width.coerceIn(0f, 2f))
        }
    }

    /**
     * Returns the persisted stereo width value, defaulting to 1.0 (natural stereo, no processing).
     */
    fun getStereoWidth(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(STEREO_WIDTH, 1f)
    }

    /**
     * Persists [drive] in [0f .. 4f].
     * 0.0 = clean/off, 4.0 = maximum saturation.
     */
    fun setTapeSaturationDrive(drive: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(TAPE_SATURATION_DRIVE, drive.coerceIn(0f, 4f))
        }
    }

    /**
     * Returns the persisted tape saturation drive, defaulting to 0.0 (off, no processing).
     */
    fun getTapeSaturationDrive(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(TAPE_SATURATION_DRIVE, 0f)
    }

    /**
     * Persists the karaoke mode enabled state.
     *
     * @param enabled True to activate center-channel removal, false to bypass.
     */
    fun setKaraokeModeEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(KARAOKE_MODE_ENABLED, enabled)
        }
    }

    /**
     * Returns whether karaoke mode is enabled, defaulting to false (bypass).
     */
    fun isKaraokeModeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(KARAOKE_MODE_ENABLED, false)
    }

    /**
     * Persists the night mode enabled state.
     *
     * @param enabled True to activate the dynamic compressor, false to bypass.
     */
    fun setNightModeEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(NIGHT_MODE_ENABLED, enabled)
        }
    }

    /**
     * Returns whether night mode is enabled, defaulting to false (bypass).
     */
    fun isNightModeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(NIGHT_MODE_ENABLED, false)
    }
}

