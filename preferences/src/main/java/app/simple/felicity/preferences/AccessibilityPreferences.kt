package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences
import app.simple.felicity.shared.constants.Colors

object AccessibilityPreferences {

    const val IS_HIGHLIGHT_MODE = "is_highlight_mode"
    const val IS_HIGHLIGHT_STROKE = "is_highlight_stroke_enabled"
    const val BOTTOM_MENU_CONTEXT = "bottom_menu_context"
    const val COLORFUL_ICONS_PALETTE = "colorful_icons_palette"

    private const val IS_DIVIDER_ENABLED = "is_divider_enabled"
    private const val REDUCE_ANIMATIONS = "reduce_animations"
    private const val IS_COLORFUL_ICONS = "is_colorful_icons"

    const val STROKE_AROUND_MINIPLAYER = "stroke_around_miniplayer"

    /** Key for the "Use Darker Shadows" miniplayer paint-shadow toggle. */
    const val DARKER_MINIPLAYER_SHADOW = "darker_miniplayer_shadow"

    // ---------------------------------------------------------------------------------------------------------- //

    fun setHighlightMode(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(IS_HIGHLIGHT_MODE, boolean) }
    }

    fun isHighlightMode(): Boolean {
        return getSharedPreferences().getBoolean(IS_HIGHLIGHT_MODE, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setHighlightStroke(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(IS_HIGHLIGHT_STROKE, boolean) }
    }

    fun isHighlightStroke(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(IS_HIGHLIGHT_STROKE, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setDivider(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(IS_DIVIDER_ENABLED, boolean).apply()
    }

    fun isDividerEnabled(): Boolean {
        return getSharedPreferences().getBoolean(IS_DIVIDER_ENABLED, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setReduceAnimations(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(REDUCE_ANIMATIONS, boolean).apply()
    }

    fun isAnimationReduced(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(REDUCE_ANIMATIONS, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAppElementsContext(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(BOTTOM_MENU_CONTEXT, value).apply()
    }

    fun isAppElementsContext(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(BOTTOM_MENU_CONTEXT, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setColorfulIcons(boolean: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(IS_COLORFUL_ICONS, boolean).apply()
    }

    fun isColorfulIcons(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(IS_COLORFUL_ICONS, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setColorfulIconsPalette(palette: Int) {
        SharedPreferences.getSharedPreferences().edit().putInt(COLORFUL_ICONS_PALETTE, palette).apply()
    }

    fun getColorfulIconsPalette(): Int {
        return SharedPreferences.getSharedPreferences().getInt(COLORFUL_ICONS_PALETTE, Colors.PASTEL)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setStrokeAroundMiniplayer(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(STROKE_AROUND_MINIPLAYER, boolean) }
    }

    fun isStrokeAroundMiniplayerOn(): Boolean {
        return getSharedPreferences().getBoolean(STROKE_AROUND_MINIPLAYER, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setDarkerMiniplayerShadow(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(DARKER_MINIPLAYER_SHADOW, boolean) }
    }

    fun isDarkerMiniplayerShadow(): Boolean {
        return getSharedPreferences().getBoolean(DARKER_MINIPLAYER_SHADOW, false)
    }
}
