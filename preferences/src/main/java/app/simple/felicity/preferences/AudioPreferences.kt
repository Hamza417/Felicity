package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.AudioPreferences.SINK_AAUDIO
import app.simple.felicity.preferences.AudioPreferences.SINK_AUDIO_TRACK
import app.simple.felicity.preferences.AudioPreferences.SINK_OBOE

object AudioPreferences {

    const val AUDIO_DECODER = "audio_decoder"
    const val GAPLESS_PLAYBACK = "gapless_playback"
    const val HIRES_OUTPUT = "hires_output"
    const val SKIP_SILENCE = "skip_silence"
    const val IS_STEREO_DOWNMIX_FORCED = "is_stereo_downmix_forced"
    const val IS_USB_DAC = "is_usb_dac"

    /**
     * The key used to store the user's chosen audio output sink.
     * The value is one of [SINK_AUDIO_TRACK], [SINK_AAUDIO], or [SINK_OBOE].
     */
    const val OUTPUT_SINK = "output_sink"

    private const val FALLBACK_TO_SW_DECODER = "fallback_to_sw_decoder"

    const val LOCAL_DECODER = 0
    const val FFMPEG = 1

    /** Standard Android AudioTrack pipeline — the safest and most compatible option. */
    const val SINK_AUDIO_TRACK = 0

    /**
     * AAudio direct-to-HAL path. Bypasses the AudioFlinger mixer for lower latency.
     * Requires API 26 (Android 8.0) or higher.
     */
    const val SINK_AAUDIO = 1

    /**
     * Oboe output path. Google's C++ audio library that automatically picks
     * AAudio on supported devices and falls back to OpenSL ES on older ones.
     */
    const val SINK_OBOE = 2

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
     * Saves the user's chosen audio output sink.
     *
     * @param sink One of [SINK_AUDIO_TRACK], [SINK_AAUDIO], or [SINK_OBOE].
     */
    fun setOutputSink(sink: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(OUTPUT_SINK, sink) }
    }

    /**
     * Returns the currently selected audio output sink.
     * Defaults to [SINK_AUDIO_TRACK] (the standard Android pipeline).
     */
    fun getOutputSink(): Int {
        return SharedPreferences.getSharedPreferences().getInt(OUTPUT_SINK, SINK_AUDIO_TRACK)
    }

    /** Convenience check — true when the user chose the AAudio output path. */
    fun isAaudioEnabled(): Boolean = getOutputSink() == SINK_AAUDIO

    /** Convenience check — true when the user chose the Oboe output path. */
    fun isOboeEnabled(): Boolean = getOutputSink() == SINK_OBOE

    fun shouldShowProcessors(): Boolean {
        return isHiresOutputEnabled().not()
                || isAaudioEnabled()
                || isOboeEnabled()
    }

    /**
     * Should use USB DAC for audio output.
     */
    fun setUsbDac(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(IS_USB_DAC, enabled) }
    }

    fun isUsbDacEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(IS_USB_DAC, false)
    }
}
