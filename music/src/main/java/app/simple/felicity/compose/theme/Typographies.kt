package app.simple.felicity.compose.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.simple.felicity.core.R

enum class TypefaceStyle {
    Black, Bold, Medium, Regular, Light, ExtraLight
}

data class FelicityTypography(
        val name: String, // e.g., "Roboto", "Montserrat"
        val black: FontFamily,
        val bold: FontFamily,
        val medium: FontFamily,
        val regular: FontFamily,
        val light: FontFamily,
        val extraLight: FontFamily
)

val NotoSansDefault = FelicityTypography(
        name = "Noto Sans",
        black = FontFamily(Font(R.font.notosans_black, FontWeight.Black)),
        bold = FontFamily(Font(R.font.notosans_bold, FontWeight.Bold)),
        medium = FontFamily(Font(R.font.notosans_medium, FontWeight.Medium)),
        regular = FontFamily(Font(R.font.notosans_regular, FontWeight.Normal)),
        light = FontFamily(Font(R.font.notosans_light, FontWeight.Light)),
        extraLight = FontFamily(Font(R.font.notosans_extralight, FontWeight.ExtraLight))
)

val NotoSansCondensed = FelicityTypography(
        name = "Noto Sans Condensed",
        black = FontFamily(Font(R.font.notosans_condensed_black, FontWeight.Black)),
        bold = FontFamily(Font(R.font.notosans_condensed_bold, FontWeight.Bold)),
        medium = FontFamily(Font(R.font.notosans_condensed_medium, FontWeight.Medium)),
        regular = FontFamily(Font(R.font.notosans_condensed_regular, FontWeight.Normal)),
        light = FontFamily(Font(R.font.notosans_condensed_light, FontWeight.Light)),
        extraLight = FontFamily(Font(R.font.notosans_condensed_extralight, FontWeight.ExtraLight))
)

val fontCollection = listOf(
        NotoSansDefault,
        NotoSansCondensed
)

