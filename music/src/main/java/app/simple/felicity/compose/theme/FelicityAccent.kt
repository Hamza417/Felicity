package app.simple.felicity.compose.theme

import androidx.compose.ui.graphics.Color

data class FelicityAccent(
        val primaryAccentColor: Color,
        val secondaryAccentColor: Color,
        val tertiaryAccentColor: Color,
)

val DefaultAccentColors = FelicityAccent(
        primaryAccentColor = Color(0xFF2980b9),
        secondaryAccentColor = Color(0xFF5499c7),
        tertiaryAccentColor = Color(0xFFFFC107)
)