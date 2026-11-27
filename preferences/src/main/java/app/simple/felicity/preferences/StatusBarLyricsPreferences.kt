package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

/**
 * Preferences for the floating status bar lyrics overlay.
 *
 * The overlay is a small floating window pinned to the status bar; it fades out as soon
 * as playback pauses, and every visual property (width, text size, colour, outline,
 * alignment, line count, translation and position) can be tuned independently.
 *
 * @author Hamza417
 */
object StatusBarLyricsPreferences {

    /** Master switch. */
    const val ENABLED = "custom_status_bar_lyrics"

    /** Width of the floating lyrics box in dp. */
    const val WIDTH = "status_bar_desktop_lyrics_text_view_width"

    /** Lyric text size in sp. */
    const val TEXT_SIZE = "status_bar_desktop_lyrics_text_size"

    /** Maximum number of wrapped lines used for a long lyric (audiobook subtitles). */
    const val LINE_COUNT = "status_bar_desktop_lyrics_line_count"

    /** Lyric text colour as a packed ARGB integer. */
    const val TEXT_COLOR = "status_bar_desktop_lyrics_text_color"

    /**
     * Alignment of the lyric inside the configured width:
     * [ALIGN_LEFT], [ALIGN_CENTER] or [ALIGN_RIGHT].
     */
    const val ALIGNMENT = "status_bar_desktop_lyrics_alignment"

    /** Colour of the outline drawn around the lyric (packed ARGB). */
    const val STROKE_COLOR = "status_bar_desktop_lyrics_stroke_color"

    /** Outline thickness in dp. */
    const val STROKE_WIDTH = "status_bar_desktop_lyrics_stroke_width"

    /** Draw a thin outline around the lyric for readability. */
    const val STROKE = "status_bar_desktop_lyrics_stroke"

    /** Horizontal position of the box, 0 = left edge, 1 = right edge. */
    const val HORIZONTAL_BIAS = "status_bar_desktop_lyrics_horizontal_bias"

    /**
     * Vertical position as a 0..[MAX_VERTICAL_OFFSET] scale across the screen:
     * 0 is the very top, [MAX_VERTICAL_OFFSET] is the very bottom.
     */
    const val VERTICAL_OFFSET = "status_bar_desktop_lyrics_y"

    /** Hide the overlay while a full player screen is visible. */
    const val HIDE_ON_PLAYER_SCREEN = "status_bar_desktop_lyrics_hide_on_player_screen"

    /** Hide the overlay while a lyrics screen is visible. */
    const val HIDE_ON_LYRICS_SCREEN = "status_bar_desktop_lyrics_hide_on_lyrics_screen"

    /** Do not render translation lines (lines sharing the original line's timestamp). */
    const val HIDE_TRANSLATION = "status_bar_lyrics_not_show_translation"

    const val DEFAULT_WIDTH = 150
    const val MIN_WIDTH = 50
    const val MAX_WIDTH = 750

    const val DEFAULT_TEXT_SIZE = 14f
    const val MIN_TEXT_SIZE = 8f
    const val MAX_TEXT_SIZE = 32f

    /** 1 = original single scrolling line; up to 4 wrapped lines for long subtitles. */
    const val DEFAULT_LINE_COUNT = 1
    const val MIN_LINE_COUNT = 1
    const val MAX_LINE_COUNT = 4

    const val DEFAULT_HORIZONTAL_BIAS = 0.5f
    const val DEFAULT_VERTICAL_OFFSET = 0
    /**
     * Vertical position scale. The stored value is kept in the original 0..2000 range
     * for compatibility, but the overlay maps it onto the actual screen height so 0 is
     * the top edge and 2000 is the bottom edge on every device.
     */
    const val MIN_VERTICAL_OFFSET = 0
    const val MAX_VERTICAL_OFFSET = 2000

    /** Default lyric colour (#0470E6). */
    val DEFAULT_TEXT_COLOR: Int = 0xFF0470E6.toInt()

    /** Alignment values, mirroring the toggle group order (left / centre / right). */
    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_RIGHT = 2
    const val DEFAULT_ALIGNMENT = ALIGN_CENTER

    /** Default outline is a semi-transparent black, matching the original overlay. */
    val DEFAULT_STROKE_COLOR: Int = 0xD9000000.toInt()

    /** Outline thickness in dp (kept small so it stays a subtle halo). */
    const val DEFAULT_STROKE_WIDTH = 1f
    const val MIN_STROKE_WIDTH = 0f
    const val MAX_STROKE_WIDTH = 6f

    fun setEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(ENABLED, enabled) }
    }

    fun isEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(ENABLED, false)
    }

    fun setWidth(widthDp: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(WIDTH, widthDp.coerceIn(MIN_WIDTH, MAX_WIDTH))
        }
    }

    fun getWidth(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(WIDTH, DEFAULT_WIDTH)
            .coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

    fun setTextSize(sizeSp: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(TEXT_SIZE, sizeSp.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
        }
    }

    fun getTextSize(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(TEXT_SIZE, DEFAULT_TEXT_SIZE)
            .coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
    }

    fun setLineCount(count: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(LINE_COUNT, count.coerceIn(MIN_LINE_COUNT, MAX_LINE_COUNT))
        }
    }

    fun getLineCount(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(LINE_COUNT, DEFAULT_LINE_COUNT)
            .coerceIn(MIN_LINE_COUNT, MAX_LINE_COUNT)
    }

    fun setTextColor(color: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(TEXT_COLOR, color) }
    }

    fun getTextColor(): Int {
        return SharedPreferences.getSharedPreferences().getInt(TEXT_COLOR, DEFAULT_TEXT_COLOR)
    }

    fun setAlignment(alignment: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(ALIGNMENT, alignment.coerceIn(ALIGN_LEFT, ALIGN_RIGHT))
        }
    }

    fun getAlignment(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(ALIGNMENT, DEFAULT_ALIGNMENT)
            .coerceIn(ALIGN_LEFT, ALIGN_RIGHT)
    }

    fun setStroke(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(STROKE, enabled) }
    }

    fun isStroke(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(STROKE, true)
    }

    fun setStrokeColor(color: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(STROKE_COLOR, color) }
    }

    fun getStrokeColor(): Int {
        return SharedPreferences.getSharedPreferences().getInt(STROKE_COLOR, DEFAULT_STROKE_COLOR)
    }

    fun setStrokeWidth(widthDp: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(STROKE_WIDTH, widthDp.coerceIn(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH))
        }
    }

    fun getStrokeWidth(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(STROKE_WIDTH, DEFAULT_STROKE_WIDTH)
            .coerceIn(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH)
    }

    fun setHorizontalBias(bias: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(HORIZONTAL_BIAS, bias.coerceIn(0f, 1f))
        }
    }

    fun getHorizontalBias(): Float {
        return SharedPreferences.getSharedPreferences()
            .getFloat(HORIZONTAL_BIAS, DEFAULT_HORIZONTAL_BIAS)
            .coerceIn(0f, 1f)
    }

    fun setVerticalOffset(offsetPx: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(VERTICAL_OFFSET, offsetPx.coerceIn(MIN_VERTICAL_OFFSET, MAX_VERTICAL_OFFSET))
        }
    }

    fun getVerticalOffset(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(VERTICAL_OFFSET, DEFAULT_VERTICAL_OFFSET)
            .coerceIn(MIN_VERTICAL_OFFSET, MAX_VERTICAL_OFFSET)
    }

    fun setHideOnPlayerScreen(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(HIDE_ON_PLAYER_SCREEN, enabled) }
    }

    fun isHideOnPlayerScreen(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(HIDE_ON_PLAYER_SCREEN, false)
    }

    fun setHideOnLyricsScreen(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(HIDE_ON_LYRICS_SCREEN, enabled) }
    }

    fun isHideOnLyricsScreen(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(HIDE_ON_LYRICS_SCREEN, false)
    }

    fun setHideTranslation(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(HIDE_TRANSLATION, enabled) }
    }

    fun isHideTranslation(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(HIDE_TRANSLATION, false)
    }

    /**
     * Resets only the position, leaving size and colours untouched.
     */
    fun resetPosition() {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(HORIZONTAL_BIAS, DEFAULT_HORIZONTAL_BIAS)
            putInt(VERTICAL_OFFSET, DEFAULT_VERTICAL_OFFSET)
        }
    }

    /**
     * Formats a colour as an uppercase `#RRGGBB` string for the settings UI.
     * When the colour has transparency the alpha byte is included (`#AARRGGBB`)
     * so the value shown always describes the colour actually drawn.
     */
    fun colorHex(color: Int): String {
        return if (color ushr 24 == 0xFF) {
            String.format("#%06X", 0xFFFFFF and color)
        } else {
            String.format("#%08X", color)
        }
    }
}
