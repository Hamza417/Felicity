package app.simple.felicity.ui.songs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.simple.felicity.compose.components.AlbumCover
import app.simple.felicity.compose.components.CoverFlow
import app.simple.felicity.compose.components.rememberCoverFlowState
import app.simple.felicity.compose.nav.LocalAppNavController
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

@Composable
fun Songs() {
    val navController = LocalAppNavController.current
    val songsViewModel: SongsViewModel = hiltViewModel()
    val songs = songsViewModel.getSongs().observeAsState().value

    songs?.let {
        // Remember state so scroll/animation survive recompositions
        val state = rememberCoverFlowState(
                itemWidth = 160.dp,
                itemSpacing = 16.dp,
                itemCountProvider = { songs.size },
                initialIndex = 3
        )

        CoverFlow(
                modifier = Modifier.fillMaxSize(),
                itemCount = songs.size,
                itemWidth = 250.dp,
                itemSpacing = 16.dp,
                state = state,
                perspectiveFactor = 32F
        ) { index, focused ->
            AlbumCover(songs[index])
        }
    }
}