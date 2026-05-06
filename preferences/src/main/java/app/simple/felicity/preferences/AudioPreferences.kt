package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object AudioPreferences {

    const val AUDIO_DECODER = "audio_decoder"
    const val GAPLESS_PLAYBACK = "gapless_playback"
    const val HIRES_OUTPUT = "hires_output"
    const val SKIP_SILENCE = "skip_silence"
    const val IS_STEREO_DOWNMIX_FORCED = "is_stereo_downmix_forced"

    /**
     * Boolean flag that enables the AAudio low-latency output path.
     *
     * When true the audio engine writes processed PCM directly to an [AAudioStream] opened
     * with [AAUDIO_PERFORMANCE_MODE_LOW_LATENCY], bypassing the standard
     * AudioTrack / AudioFlinger mixer pipeline. This reduces output latency at the cost of
     * exclusive hardware access (shared mode is used as a fallback when exclusive mode is
     * unavailable). Defaults to false so the standard AudioTrack path remains active by default.
     */
    const val AAUDIO_ENABLED = "aaudio_enabled"

    const val CROSSFADE_ENABLED = "crossfade_enabled"
    const val CROSSFADE_DURATION_MS = "crossfade_duration_ms"

    private const val FALLBACK_TO_SW_DECODER = "fallback_to_sw_decoder"
    private const val DEFAULT_CROSSFADE_DURATION_MS = 3000

    const val LOCAL_DECODER = 0
    const val FFMPEG = 1

    // --------------------------------------------------------------------------------------------- //

    fun setAudioDecoder(decoder: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(AUDIO_DECODER, decoder) }
    }

    fun getAudioDecoder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(AUDIO_DECODER, LOCAL_DECODER)
    }

    // --------------------------------------------------------------------------------------------- //

    fun setGaplessPlayback(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(GAPLESS_PLAYBACK, enabled) }
    }

    fun isGaplessPlaybackEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(GAPLESS_PLAYBACK, true)
    }

    // --------------------------------------------------------------------------------------------- //

    fun setHiresOutput(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(HIRES_OUTPUT, enabled) }
    }

    fun isHiresOutputEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(HIRES_OUTPUT, false)
    }

    // --------------------------------------------------------------------------------------------- //

    fun setSkipSilence(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SKIP_SILENCE, enabled) }
    }

    fun isSkipSilenceEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SKIP_SILENCE, false)
    }

    // --------------------------------------------------------------------------------------------- //

    fun setFallbackToSoftwareDecoder(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(FALLBACK_TO_SW_DECODER, enabled) }
    }

    fun isFallbackToSoftwareDecoderEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(FALLBACK_TO_SW_DECODER, true)
    }

    // --------------------------------------------------------------------------------------------- //

    fun setIsStereoDownmixForced(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(IS_STEREO_DOWNMIX_FORCED, enabled) }
    }

    fun isStereoDownmixForced(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(IS_STEREO_DOWNMIX_FORCED, true)
    }

    /**
     * Enables or disables the crossfade feature, which fades one song out while the next
     * one fades in so there is never a silent gap between tracks.
     *
     * When this is on, gapless playback is effectively bypassed because the two features
     * fight over the same track boundary — crossfade wins and handles the transition itself.
     */
    fun setCrossfadeEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(CROSSFADE_ENABLED, enabled) }
    }

    /**
     * Returns whether crossfade transitions are currently enabled.
     * Defaults to false so the user's first experience is a clean, unmodified playback.
     */
    fun isCrossfadeEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(CROSSFADE_ENABLED, true)
    }

    // --------------------------------------------------------------------------------------------- //

    /**
     * Saves how long the crossfade overlap should last, in milliseconds.
     * For example, a value of 3000 means the two songs blend together for three seconds
     * before the outgoing one is completely silent.
     *
     * @param durationMs Duration of the crossfade in milliseconds. Clamped to [500, 10000].
     */
    fun setCrossfadeDurationMs(durationMs: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(CROSSFADE_DURATION_MS, durationMs.coerceIn(500, 10_000)) }
    }

    /**
     * Returns how long the crossfade overlap should last in milliseconds.
     * Defaults to 3 seconds, which feels natural for most music genres.
     */
    fun getCrossfadeDurationMs(): Int {
        return SharedPreferences.getSharedPreferences().getInt(CROSSFADE_DURATION_MS, DEFAULT_CROSSFADE_DURATION_MS)
    }

    // --------------------------------------------------------------------------------------------- //

    /**
     * Persists whether the AAudio low-latency output path is enabled.
     *
     * When enabled, processed PCM is written to an [AAudioStream] with
     * [AAUDIO_PERFORMANCE_MODE_LOW_LATENCY] instead of going through the standard
     * AudioTrack pipeline. Defaults to false.
     *
     * @param enabled True to route audio through the AAudio direct-to-HAL path.
     */
    fun setAaudioEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(AAUDIO_ENABLED, enabled) }
    }

    /**
     * Returns whether the AAudio low-latency output path is currently enabled.
     * Defaults to false (standard AudioTrack path).
     */
    fun isAaudioEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(AAUDIO_ENABLED, false)
    }
}
