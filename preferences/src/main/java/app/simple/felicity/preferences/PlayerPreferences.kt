package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object PlayerPreferences {
    const val REPEAT_MODE = "repeat_mode"

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

    fun setRepeatMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(REPEAT_MODE, value) }
    }

    fun getRepeatMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(REPEAT_MODE, 0)
    }

    /**
     * Persist [pan] in [-1f .. 1f].
     * -1 = full left, 0 = centre, +1 = full right.
     */
    fun setBalance(pan: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(BALANCE, pan.coerceIn(-1f, 1f))
        }
    }

    /** Returns the persisted pan value, defaulting to 0 (centre). */
    fun getBalance(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(BALANCE, 0f)
    }

    /**
     * Persist [width] in [0f .. 2f].
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
     * Tape saturation drive stored as a float in [0 .. 4].
     * 0.0 = clean bypass (default, no processing), 1.0 = subtle warmth,
     * 2.0 = punchy saturation, 4.0 = maximum drive.
     * Applied via algebraic soft-clip transfer function in the audio engine.
     */
    const val TAPE_SATURATION_DRIVE = "player_tape_saturation_drive"

    /**
     * Persist [drive] in [0f .. 4f].
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
}