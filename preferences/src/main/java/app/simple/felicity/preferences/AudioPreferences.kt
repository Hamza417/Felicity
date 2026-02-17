package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object AudioPreferences {

    const val AUDIO_DECODER = "audio_decoder"
    const val GAPLESS_PLAYBACK = "gapless_playback"

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
}
