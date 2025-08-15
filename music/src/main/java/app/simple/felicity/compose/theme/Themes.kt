package app.simple.felicity.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * FelicityColors holds all color values used for theming the app UI.
 *
 * @param backgroundColor        Used for main background surfaces.
 * @param onBackgroundColor      Used for text/icons on background surfaces.
 * @param dividerColor           Used for divider lines between UI elements.
 * @param headerTextColor        Used for header text.
 * @param primaryTextColor       Used for main body text.
 * @param secondaryTextColor     Used for secondary text.
 * @param tertiaryTextColor      Used for less prominent text.
 * @param quaternaryTextColor    Used for least prominent text.
 * @param iconRegularColor       Used for regular icons.
 * @param iconSecondaryColor     Used for secondary icons.
 * @param iconDisabledColor      Used for disabled icons.
 */
data class FelicityColors(
        val backgroundColor: Color,
        val onBackgroundColor: Color,
        val dividerColor: Color,
        val headerTextColor: Color,
        val primaryTextColor: Color,
        val secondaryTextColor: Color,
        val tertiaryTextColor: Color,
        val quaternaryTextColor: Color,
        val iconRegularColor: Color,
        val iconSecondaryColor: Color,
        val iconDisabledColor: Color,
)

val LightThemeColors = FelicityColors(
        backgroundColor = Color(0xFFFFFFFF),
        onBackgroundColor = Color(0xFF212121),
        dividerColor = Color(0xFFE0E0E0),
        headerTextColor = Color(0xFF333333),
        primaryTextColor = Color(0xFF212121),
        secondaryTextColor = Color(0xFF757575),
        tertiaryTextColor = Color(0xFFBDBDBD),
        quaternaryTextColor = Color(0xFF9E9E9E),
        iconRegularColor = Color(0xFF616161),
        iconSecondaryColor = Color(0xFF757575),
        iconDisabledColor = Color(0xFFC7C7C7)
)

val DarkThemeColors = FelicityColors(
        backgroundColor = Color(0xFF121212),
        onBackgroundColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFF333333),
        headerTextColor = Color(0xFFFFFFFF),
        primaryTextColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFB0B0B0),
        tertiaryTextColor = Color(0xFF757575),
        quaternaryTextColor = Color(0xFF616161),
        iconRegularColor = Color(0xFFE0E0E0),
        iconSecondaryColor = Color(0xFFB0B0B0),
        iconDisabledColor = Color(0xFF888888)
)

val lightThemes = listOf(
        LightThemeColors
)

val darkThemes = listOf(
        DarkThemeColors
)

enum class TextColor {
    Header, Primary, Secondary, Tertiary, Quaternary
}

enum class TextSize {
    Small, Medium, Large, ExtraLarge
}