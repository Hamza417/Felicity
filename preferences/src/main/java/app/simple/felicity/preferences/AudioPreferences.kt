package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object AudioPreferences {

    const val AUDIO_DECODER = "audio_decoder"
    const val GAPLESS_PLAYBACK = "gapless_playback"
    const val HIRES_OUTPUT = "hires_output"
    const val SKIP_SILENCE = "skip_silence"
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
}
