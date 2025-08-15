package app.simple.felicity.compose.primitives

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import app.simple.felicity.compose.theme.FelicityColors
import app.simple.felicity.compose.theme.LocalFelicityColors
import app.simple.felicity.compose.theme.LocalFelicityTypography
import app.simple.felicity.compose.theme.TextColor
import app.simple.felicity.compose.theme.TypefaceStyle

fun getTextColor(color: TextColor, theme: FelicityColors): Color = when (color) {
    TextColor.Header -> theme.headerTextColor
    TextColor.Primary -> theme.primaryTextColor
    TextColor.Secondary -> theme.secondaryTextColor
    TextColor.Tertiary -> theme.tertiaryTextColor
    TextColor.Quaternary -> theme.quaternaryTextColor
}

@Composable
fun TypeFaceText(
        resId: Int,
        modifier: Modifier = Modifier,
        typeface: TypefaceStyle = TypefaceStyle.Regular,
        style: TextStyle = MaterialTheme.typography.bodyMedium,
        color: TextColor = TextColor.Secondary
) {
    val text = stringResource(id = resId)
    TypeFaceTextInternal(text, modifier, typeface, style, color)
}

@Composable
fun TypeFaceText(
        text: String,
        modifier: Modifier = Modifier,
        typeface: TypefaceStyle = TypefaceStyle.Regular,
        style: TextStyle = MaterialTheme.typography.bodyMedium,
        color: TextColor = TextColor.Secondary
) {
    TypeFaceTextInternal(text, modifier, typeface, style, color)
}

@Composable
private fun TypeFaceTextInternal(
        text: String,
        modifier: Modifier,
        typeface: TypefaceStyle,
        style: TextStyle,
        color: TextColor
) {
    val typography = LocalFelicityTypography.current
    val theme = LocalFelicityColors.current

    val (fontFamily) = when (typeface) {
        TypefaceStyle.Black -> typography.black to FontWeight.Black
        TypefaceStyle.Bold -> typography.bold to FontWeight.Bold
        TypefaceStyle.Medium -> typography.medium to FontWeight.Medium
        TypefaceStyle.Regular -> typography.regular to FontWeight.Normal
        TypefaceStyle.Light -> typography.light to FontWeight.Light
        TypefaceStyle.ExtraLight -> typography.extraLight to FontWeight.ExtraLight
    }

    Text(
            text = text,
            modifier = modifier,
            color = getTextColor(color, theme),
            fontFamily = fontFamily,
            style = style
    )
}
