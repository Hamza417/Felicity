package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object LyricsPreferences {

    const val LRC_ALIGNMENT = "lyrics_lrc_alignment"
    const val LRC_TEXT_SIZE = "lyrics_lrc_text_size"

    /** Preference key that stores whether the app should reach out to LrcLib automatically. */
    const val AUTO_DOWNLOAD_LYRICS = "lyrics_auto_download"

    const val LEFT = 0
    const val CENTER = 1
    const val RIGHT = 2


    fun setLrcAlignment(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(LRC_ALIGNMENT, value)
        }
    }

    fun getLrcAlignment(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(LRC_ALIGNMENT, CENTER)
    }

    fun setLrcTextSize(value: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(LRC_TEXT_SIZE, value)
        }
    }

    fun getLrcTextSize(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(LRC_TEXT_SIZE, 16f)
    }

    /**
     * Saves whether the app is allowed to automatically fetch lyrics from
     * LrcLib when no local file or embedded lyrics are found.
     *
     * Turning this off means the user only gets lyrics they've manually added
     * or that were baked into the audio file — no surprise network calls.
     */
    fun setAutoDownloadLyrics(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(AUTO_DOWNLOAD_LYRICS, enabled)
        }
    }

    /**
     * Returns true if the app should try to fetch lyrics online automatically.
     * Defaults to true so new users get lyrics out of the box without any setup.
     */
    fun isAutoDownloadLyrics(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(AUTO_DOWNLOAD_LYRICS, true)
    }
}