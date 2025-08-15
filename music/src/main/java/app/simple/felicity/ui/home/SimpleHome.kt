package app.simple.felicity.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.simple.felicity.compose.primitives.TypeFaceText
import app.simple.felicity.compose.theme.LocalBarsSize
import app.simple.felicity.compose.theme.TextColor
import app.simple.felicity.compose.theme.TypefaceStyle
import app.simple.felicity.core.R

@Composable
fun SimpleHome() {

    val options = listOf(
            R.string.songs,
            R.string.artists,
            R.string.albums,
            R.string.playlists,
            R.string.folders,
            R.string.genres,
    )

    MaterialTheme.typography.bodyLarge.fontSize

    LazyColumn(
            modifier = Modifier
                .padding(top = LocalBarsSize.current.statusBarHeight,
                         bottom = LocalBarsSize.current.navigationBarHeight)
    ) {
        items(options.size) { index ->
            TypeFaceText(
                    resId = options[index],
                    modifier = Modifier.padding(16.dp),
                    typeface = TypefaceStyle.Regular,
                    color = TextColor.Primary,
                    style = MaterialTheme.typography.titleMedium
            )
        }
    }
}