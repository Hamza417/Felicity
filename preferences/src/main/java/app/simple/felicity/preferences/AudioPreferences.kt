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

    private const val FALLBACK_TO_SW_DECODER = "fallback_to_sw_decoder"

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
