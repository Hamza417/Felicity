package app.simple.felicity.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.simple.felicity.compose.primitives.IconColor
import app.simple.felicity.compose.primitives.ThemedIcon
import app.simple.felicity.compose.primitives.TypeFaceText
import app.simple.felicity.compose.theme.TextColor
import app.simple.felicity.compose.theme.TypefaceStyle

@Composable
private fun HeaderInternal(
        text: String,
        modifier: Modifier,
        style: TextStyle,
        painter: Painter? = null,
        imageVector: ImageVector? = null,
        iconColor: IconColor = IconColor.Accent
) {
    Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            painter != null -> {
                ThemedIcon(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(48.dp, 48.dp),
                        iconColor = iconColor,
                )
            }
            imageVector != null -> {
                ThemedIcon(
                        imageVector = imageVector,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(48.dp, 48.dp),
                        iconColor = iconColor,
                )
            }
        }
        Spacer(
                modifier = Modifier.size(16.dp) // Space between icon and text
        )
        TypeFaceText(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                style = style,
                color = TextColor.Header,
                typeface = TypefaceStyle.Black
        )
    }
}

@Composable
fun Header(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = MaterialTheme.typography.displayMedium,
        painter: Painter? = null,
        iconColor: IconColor = IconColor.Regular
) {
    HeaderInternal(
            text = text,
            modifier = modifier,
            style = style,
            painter = painter,
            iconColor = iconColor
    )
}

@Composable
fun Header(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = MaterialTheme.typography.displayMedium,
        imageVector: ImageVector? = null,
        iconColor: IconColor = IconColor.Regular
) {
    HeaderInternal(
            text = text,
            modifier = modifier,
            style = style,
            imageVector = imageVector,
            iconColor = iconColor
    )
}