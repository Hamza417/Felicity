package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object AlbumArtPreferences {

    private const val ALBUM_ART_SHADOW = "album_art_shadow"
    private const val ALBUM_ART_ROUNDED_CORNERS = "album_art_rounded_corners"
    private const val ALBUM_ART_CROP = "album_art_crop"
    private const val ALBUM_ART_GREYSCALE = "album_art_greyscale"

    const val ALBUM_ART_PALETTE_STYLE = "album_art_palette_style"

    const val PALETTE_STYLE_PASTEL = 0
    const val PALETTE_STYLE_CHIC = 1
    const val PALETTE_STYLE_RETRO = 2

    fun isShadowEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(ALBUM_ART_SHADOW, true)
    }

    fun setShadowEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ART_SHADOW, enabled)
        }
    }

    fun isRoundedCornersEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(ALBUM_ART_ROUNDED_CORNERS, true)
    }

    fun setRoundedCornersEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ART_ROUNDED_CORNERS, enabled)
        }
    }

    fun isCropEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(ALBUM_ART_CROP, true)
    }

    fun setCropEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ART_CROP, enabled)
        }
    }

    fun isGreyscaleEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(ALBUM_ART_GREYSCALE, false)
    }

    fun setGreyscaleEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ART_GREYSCALE, enabled)
        }
    }

    fun getPaletteStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(ALBUM_ART_PALETTE_STYLE, PALETTE_STYLE_CHIC)
    }

    fun setPaletteStyle(style: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(ALBUM_ART_PALETTE_STYLE, style)
        }
    }
}