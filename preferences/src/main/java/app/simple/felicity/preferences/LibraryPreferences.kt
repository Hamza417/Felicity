package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object LibraryPreferences {

    const val MINIMUM_AUDIO_LENGTH = "minimum_audio_length"
    const val MINIMUM_AUDIO_SIZE = "minimum_audio_size"

    fun getMinimumAudioLength(): Int {
        return SharedPreferences.getSharedPreferences().getInt(MINIMUM_AUDIO_LENGTH, 0)
    }

    fun setMinimumAudioLength(length: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(MINIMUM_AUDIO_LENGTH, length) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun getMinimumAudioSize(): Int {
        return SharedPreferences.getSharedPreferences().getInt(MINIMUM_AUDIO_SIZE, 0)
    }

    fun setMinimumAudioSize(size: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(MINIMUM_AUDIO_SIZE, size) }
    }
}