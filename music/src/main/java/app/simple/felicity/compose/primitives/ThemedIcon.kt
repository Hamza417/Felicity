package app.simple.felicity.compose.primitives

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import app.simple.felicity.compose.theme.LocalFelicityAccentColors
import app.simple.felicity.compose.theme.LocalFelicityColors

enum class IconColor {
    Regular, Secondary, Disabled, Accent
}

@Composable
private fun ThemedIconInternal(
        modifier: Modifier,
        iconColor: IconColor,
        overrideTint: Color?,
        contentDescription: String?,
        painter: Painter? = null,
        imageVector: ImageVector? = null
) {
    val iconTheme = LocalFelicityColors.current
    val accent = LocalFelicityAccentColors.current

    val resolvedTint = overrideTint ?: when (iconColor) {
        IconColor.Regular -> iconTheme.iconRegularColor
        IconColor.Secondary -> iconTheme.iconSecondaryColor
        IconColor.Disabled -> iconTheme.iconDisabledColor
        IconColor.Accent -> accent.primaryAccentColor
    }

    if (painter != null) {
        Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = modifier,
                tint = resolvedTint
        )
    } else if (imageVector != null) {
        Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = modifier,
                tint = resolvedTint
        )
    }
}

@Composable
fun ThemedIcon(
        imageVector: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        iconColor: IconColor = IconColor.Regular,
        overrideTint: Color? = null
) {
    ThemedIconInternal(
            modifier = modifier,
            iconColor = iconColor,
            overrideTint = overrideTint,
            contentDescription = contentDescription,
            imageVector = imageVector
    )
}

@Composable
fun ThemedIcon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        iconColor: IconColor = IconColor.Regular,
        overrideTint: Color? = null
) {
    ThemedIconInternal(
            modifier = modifier,
            iconColor = iconColor,
            overrideTint = overrideTint,
            contentDescription = contentDescription,
            painter = painter
    )
}