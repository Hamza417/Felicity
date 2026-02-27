package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object LyricsPreferences {

    const val LRC_ALIGNMENT = "lyrics_lrc_alignment"

    const val LEFT = 0
    const val CENTER = 1
    const val RIGHT = 2

    // ------------------------------------------------------------------------------------------ //

    fun setLrcAlignment(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(LRC_ALIGNMENT, value)
        }
    }

    fun getLrcAlignment(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(LRC_ALIGNMENT, CENTER)
    }
}