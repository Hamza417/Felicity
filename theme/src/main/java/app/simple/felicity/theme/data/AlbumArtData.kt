package app.simple.felicity.theme.data

import androidx.core.graphics.toColorInt
import app.simple.felicity.theme.data.AlbumArtData.populate
import app.simple.felicity.theme.tools.MonetPalette

/**
 * Singleton that stores all Album Art-derived theme colors for both light and dark modes.
 * Fields are initialized with sensible fallback values (matching the built-in light and dark
 * themes) so the UI looks reasonable before any palette extraction has run.
 *
 * Call [populate] with a freshly built [MonetPalette] to replace all fields with colors
 * derived from the current album art's dominant hue using MD3 tonal palette logic.
 *
 * @author Hamza417
 */
object AlbumArtData {

    // Light theme defaults
    var headingTextColor = "#121212".toColorInt()
    var primaryTextColor = "#2B2B2B".toColorInt()
    var secondaryTextColor = "#5A5A5A".toColorInt()
    var tertiaryTextColor = "#7A7A7A".toColorInt()
    var quaternaryTextColor = "#9A9A9A".toColorInt()
    var background = "#FFFFFF".toColorInt()
    var highlightBackground = "#F6F6F6".toColorInt()
    var selectedBackground = "#F1F1F1".toColorInt()
    var dividerBackground = "#DDDDDD".toColorInt()
    var spotColor = "#D4D4D4".toColorInt()
    var switchOffColor = "#EDEDED".toColorInt()
    var regularIconColor = "#2E2E2E".toColorInt()
    var secondaryIconColor = "#B1B1B1".toColorInt()
    var disabledIconColor = "#F6F6F6".toColorInt()

    // Dark theme defaults
    var headingTextColorDark = "#F1F1F1".toColorInt()
    var primaryTextColorDark = "#E4E4E4".toColorInt()
    var secondaryTextColorDark = "#C8C8C8".toColorInt()
    var tertiaryTextColorDark = "#AAAAAA".toColorInt()
    var quaternaryTextColorDark = "#9A9A9A".toColorInt()
    var backgroundDark = "#171717".toColorInt()
    var highlightBackgroundDark = "#404040".toColorInt()
    var selectedBackgroundDark = "#242424".toColorInt()
    var dividerBackgroundDark = "#666666".toColorInt()
    var spotColorDark = "#1A1A1A".toColorInt()
    var switchOffColorDark = "#252525".toColorInt()
    var regularIconColorDark = "#F8F8F8".toColorInt()
    var secondaryIconColorDark = "#E8E8E8".toColorInt()
    var disabledIconColorDark = "#404040".toColorInt()

    /**
     * Populates every light and dark theme field from the given [palette].
     *
     * Color role mapping follows MD3 tonal-palette conventions:
     * - Neutral tones (very low chroma) drive text, backgrounds, and icons.
     * - Neutral-variant tones (slightly higher chroma) drive surfaces, dividers, and switch tracks.
     * - Primary tones (full chroma) drive the spot/accent color for each mode.
     *
     * @param palette the [MonetPalette] computed from the current album art bitmap.
     */
    fun populate(palette: MonetPalette) {
        // Light theme — low tones for text (dark on white), high tones for backgrounds
        headingTextColor = palette.getNeutralTone(10.0)
        primaryTextColor = palette.getNeutralTone(20.0)
        secondaryTextColor = palette.getNeutralTone(40.0)
        tertiaryTextColor = palette.getNeutralTone(50.0)
        quaternaryTextColor = palette.getNeutralTone(60.0)
        background = palette.getNeutralTone(98.0)
        highlightBackground = palette.getNeutralVariantTone(92.0)
        selectedBackground = palette.getNeutralVariantTone(85.0)
        dividerBackground = palette.getNeutralVariantTone(80.0)
        spotColor = palette.getPrimaryTone(40.0)
        switchOffColor = palette.getNeutralVariantTone(90.0)
        regularIconColor = palette.getNeutralTone(20.0)
        secondaryIconColor = palette.getNeutralTone(50.0)
        disabledIconColor = palette.getNeutralVariantTone(90.0)

        // Dark theme — high tones for text (light on dark), low tones for backgrounds
        headingTextColorDark = palette.getNeutralTone(95.0)
        primaryTextColorDark = palette.getNeutralTone(90.0)
        secondaryTextColorDark = palette.getNeutralTone(80.0)
        tertiaryTextColorDark = palette.getNeutralTone(70.0)
        quaternaryTextColorDark = palette.getNeutralTone(60.0)
        backgroundDark = palette.getNeutralTone(6.0)
        highlightBackgroundDark = palette.getNeutralVariantTone(30.0)
        selectedBackgroundDark = palette.getNeutralTone(20.0)
        dividerBackgroundDark = palette.getNeutralTone(40.0)
        spotColorDark = palette.getPrimaryTone(80.0)
        switchOffColorDark = palette.getNeutralTone(30.0)
        regularIconColorDark = palette.getNeutralTone(90.0)
        secondaryIconColorDark = palette.getNeutralTone(70.0)
        disabledIconColorDark = palette.getNeutralTone(40.0)
    }
}

