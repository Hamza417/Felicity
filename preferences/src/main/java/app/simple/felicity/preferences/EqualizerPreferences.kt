package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.EqualizerPreferences.EQ_BAND_KEY_PREFIX
import app.simple.felicity.preferences.EqualizerPreferences.REVERB_DECAY

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

    /**
     * Global pre-amplifier gain applied to the signal before the 10-band EQ.
     * Stored as a float in dB in the range [-15 .. +15]. Default is 0.0 (unity gain).
     * Use this to compensate for overall loudness without affecting the EQ curve shape.
     */
    const val PREAMP_DB = "eq_preamp_db"

    /**
     * Bass low-shelf gain stored as a float in [-12 .. +12] dB.
     * Applied via a second-order RBJ low-shelf biquad at 250 Hz (S = 1).
     * 0.0 dB = flat bypass (default).
     */
    const val BASS_DB = "eq_bass_db"

    /**
     * Treble high-shelf gain stored as a float in [-12 .. +12] dB.
     * Applied via a second-order RBJ high-shelf biquad at 4000 Hz (S = 1).
     * 0.0 dB = flat bypass (default).
     */
    const val TREBLE_DB = "eq_treble_db"

    /**
     * Reverb wet/dry mix stored as a float in [0 .. 1].
     * 0.0 = fully dry (reverb bypassed, default), 1.0 = fully wet (only reverb output).
     * Applied by the native DSP engine after all equalization and saturation stages.
     */
    const val REVERB_MIX = "eq_reverb_mix"

    /**
     * Reverb decay time stored as a float in [0 .. 1].
     * 0.0 = very short tail, 1.0 = long hall reverb.
     * Internally maps to comb-filter feedback [0.40 .. 0.95] and damping coefficients.
     * Default is 0.5 (medium decay).
     */
    const val REVERB_DECAY = "eq_reverb_decay"

    /**
     * Reverb high-frequency damping stored as a float in [0 .. 1].
     * 0.0 = brightest tail (minimal high-freq absorption), 1.0 = darkest tail (heavy damping).
     * Independent of [REVERB_DECAY] so the user can set a long but dark room or a short but
     * bright one. Default is 0.3 (moderately bright, natural-sounding tail).
     */
    const val REVERB_DAMP = "eq_reverb_damp"

    /**
     * Reverb room size stored as a float in [0 .. 1].
     * 0.0 = small room (short delay lines), 1.0 = large hall (long delay lines).
     * Default is 0.5 (medium room).
     */
    const val REVERB_SIZE = "eq_reverb_size"

    /**
     * Playback pitch stored as a semitone offset in [-12 .. +12].
     * 0 = no shift (default, concert pitch), -12 = one full octave down, +12 = one full
     * octave up. Converted to an ExoPlayer raw multiplier via the formula 2^(n/12) before
     * being passed to PlaybackParameters, which keeps the values human-readable in storage.
     */
    const val PITCH = "eq_pitch"

    /**
     * Playback speed stored as a raw float multiplier in [0.5 .. 2.0].
     * 1.0 = normal speed (default), 0.5 = half speed, 2.0 = double speed.
     * Passed directly to ExoPlayer's PlaybackParameters.
     */
    const val PLAYBACK_SPEED = "eq_playback_speed"

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
     * Persists the pre-amplifier gain in dB, clamped to [-15 .. +15].
     * Applied to the signal before all EQ band filters.
     *
     * @param db Gain in dB, clamped to [-15 .. +15]. 0 dB = unity (no change).
     */
    fun setPreampDb(db: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(PREAMP_DB, db.coerceIn(-15f, 15f)) }
    }

    /**
     * Returns the persisted pre-amplifier gain in dB.
     * Defaults to 0.0 dB (unity gain) when no value has been saved yet.
     */
    fun getPreampDb(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(PREAMP_DB, 0f)
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

    // -------------------------------------------------------------------------
    // Bass and treble tone controls
    // -------------------------------------------------------------------------

    /**
     * Persists the bass low-shelf gain in dB, clamped to [-12 .. +12].
     * Applied by the BassAudioProcessor as a second-order low-shelf biquad at 250 Hz.
     *
     * @param db Gain in dB. 0.0 = flat bypass (default).
     */
    fun setBassDb(db: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(BASS_DB, db.coerceIn(-12f, 12f)) }
    }

    /**
     * Returns the persisted bass low-shelf gain in dB.
     * Defaults to 0.0 dB (flat bypass) when no value has been saved yet.
     */
    fun getBassDb(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(BASS_DB, 0f)
    }

    /**
     * Persists the treble high-shelf gain in dB, clamped to [-12 .. +12].
     * Applied by the TrebleAudioProcessor as a second-order high-shelf biquad at 4000 Hz.
     *
     * @param db Gain in dB. 0.0 = flat bypass (default).
     */
    fun setTrebleDb(db: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(TREBLE_DB, db.coerceIn(-12f, 12f)) }
    }

    /**
     * Returns the persisted treble high-shelf gain in dB.
     * Defaults to 0.0 dB (flat bypass) when no value has been saved yet.
     */
    fun getTrebleDb(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(TREBLE_DB, 0f)
    }

    // -------------------------------------------------------------------------
    // Reverb
    // -------------------------------------------------------------------------

    /**
     * Persists the reverb wet/dry mix, clamped to [0 .. 1].
     *
     * @param mix Mix value. 0.0 = dry only (bypass); 1.0 = fully wet.
     */
    fun setReverbMix(mix: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(REVERB_MIX, mix.coerceIn(0f, 1f)) }
    }

    /**
     * Returns the persisted reverb wet/dry mix.
     * Defaults to 0.0 (bypassed) when no value has been saved yet.
     */
    fun getReverbMix(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(REVERB_MIX, 0f)
    }

    /**
     * Persists the reverb decay time parameter, clamped to [0 .. 1].
     *
     * @param decay Decay value. 0.0 = very short; 1.0 = very long hall.
     */
    fun setReverbDecay(decay: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(REVERB_DECAY, decay.coerceIn(0f, 1f)) }
    }

    /**
     * Returns the persisted reverb decay time parameter.
     * Defaults to 0.5 (medium decay) when no value has been saved yet.
     */
    fun getReverbDecay(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(REVERB_DECAY, 0.5f)
    }

    /**
     * Persists the reverb high-frequency damping parameter, clamped to [0 .. 1].
     *
     * @param damp Damping value. 0.0 = brightest tail; 1.0 = darkest tail.
     */
    fun setReverbDamp(damp: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(REVERB_DAMP, damp.coerceIn(0f, 1f)) }
    }

    /**
     * Returns the persisted reverb high-frequency damping parameter.
     * Defaults to 0.3 (moderately bright) when no value has been saved yet.
     */
    fun getReverbDamp(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(REVERB_DAMP, 0.3f)
    }

    /**
     * Persists the reverb room-size parameter, clamped to [0 .. 1].
     *
     * @param size Room size. 0.0 = small room; 1.0 = large hall.
     */
    fun setReverbSize(size: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(REVERB_SIZE, size.coerceIn(0f, 1f)) }
    }

    /**
     * Returns the persisted reverb room-size parameter.
     * Defaults to 0.5 (medium room) when no value has been saved yet.
     */
    fun getReverbSize(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(REVERB_SIZE, 0.5f)
    }

    /**
     * Persists the playback pitch as a semitone offset, clamped to [-12 .. +12].
     *
     * Storing semitones (rather than a raw multiplier) keeps the value human-readable
     * and maps directly to what the pitch knob displays on screen.
     *
     * @param semitones Offset in semitones. 0 = no shift (concert pitch).
     */
    fun setPitch(semitones: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(PITCH, semitones.coerceIn(-12f, 12f)) }
    }

    /**
     * Returns the persisted pitch offset in semitones.
     * Defaults to 0.0 (no shift) when no value has been saved yet.
     */
    fun getPitch(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(PITCH, 0f)
    }

    /**
     * Persists the playback speed multiplier, clamped to [0.5 .. 2.0].
     *
     * @param speed Speed multiplier. 1.0 = normal speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        SharedPreferences.getSharedPreferences().edit { putFloat(PLAYBACK_SPEED, speed.coerceIn(0.5f, 2.0f)) }
    }

    /**
     * Returns the persisted playback speed multiplier.
     * Defaults to 1.0 (normal speed) when no value has been saved yet.
     */
    fun getPlaybackSpeed(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(PLAYBACK_SPEED, 1.0f)
    }
}
