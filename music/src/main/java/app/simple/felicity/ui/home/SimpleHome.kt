package app.simple.felicity.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.SpeakerNotes
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simple.felicity.compose.components.Header
import app.simple.felicity.compose.nav.LocalAppNavController
import app.simple.felicity.compose.nav.Routes
import app.simple.felicity.compose.primitives.IconColor
import app.simple.felicity.compose.primitives.ThemedIcon
import app.simple.felicity.compose.primitives.TypeFaceText
import app.simple.felicity.compose.theme.LocalBarsSize
import app.simple.felicity.compose.theme.TextColor
import app.simple.felicity.compose.theme.TypefaceStyle
import app.simple.felicity.core.R

@Composable
fun SimpleHome() {
    val navController = LocalAppNavController.current

    val options = listOf(
            R.string.songs,
            R.string.artists,
            R.string.albums,
            R.string.playlists,
            R.string.folders,
            R.string.genres,
    )

    val icons = listOf(
            Icons.Rounded.MusicNote,
            Icons.Rounded.Person,
            Icons.Rounded.Album,
            Icons.AutoMirrored.Rounded.PlaylistAdd,
            Icons.Rounded.Folder,
            Icons.AutoMirrored.Rounded.SpeakerNotes,
    )

    LazyColumn(
            modifier = Modifier
                .padding(top = LocalBarsSize.current.statusBarHeight,
                         bottom = LocalBarsSize.current.navigationBarHeight,
                         start = 16.dp,
                         end = 16.dp)
                .fillMaxSize()
    ) {
        item {
            Header(
                    text = stringResource(id = R.string.app_name),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.displayMedium,
                    painter = null, // painterResource(R.drawable.ic_felicity_full),
                    iconColor = IconColor.Accent
            )
        }
        items(options.size) { index ->
            Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (options[index]) {
                                R.string.genres -> {
                                    navController.navigate(Routes.GENRE)
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
            ) {
                ThemedIcon(
                        imageVector = icons[index],
                        contentDescription = stringResource(id = options[index]),
                        modifier = Modifier.padding(16.dp),
                )
                TypeFaceText(
                        resId = options[index],
                        modifier = Modifier
                            .padding(24.dp),
                        typeface = TypefaceStyle.Bold,
                        color = TextColor.Primary,
                        style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
