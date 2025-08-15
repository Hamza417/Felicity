package app.simple.felicity.ui.genre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.simple.felicity.compose.theme.LocalBarsSize
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.viewmodels.main.genres.GenresViewModel
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState

@Composable
fun Genre() {
    val genreViewModel: GenresViewModel = hiltViewModel()
    val genresData = genreViewModel.getGenresData().observeAsState(emptyList())

    val statusBarHeight = LocalBarsSize.current.statusBarHeight
    val navigationBarHeight = LocalBarsSize.current.navigationBarHeight

    val cellsList = remember {
        listOf(
                GridCells.Fixed(4), // ↑ Zoom out
                GridCells.Fixed(3), // |
                GridCells.Fixed(2), // ↓ Zoom in
        )
    }

    val state = rememberPinchZoomGridState(
            cellsList = cellsList,
            initialCellsIndex = 1,
    )

    PinchZoomGridLayout(state = state) {
        LazyVerticalGrid(
                columns = gridCells,
                state = gridState,
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp + statusBarHeight,
                        bottom = 16.dp + navigationBarHeight
                )
        ) {
            items(genresData.value.size) { index ->
                val genre = genresData.value[index]
                GenreItem(
                        genre = genre,
                        modifier = Modifier.pinchItem(key = index)
                ) { genreId ->
                    // Handle genre click, e.g., navigate to genre details
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun GenreItem(
        genre: Genre,
        modifier: Modifier,
        onClick: (String) -> Unit
) {
    ElevatedCard(
            modifier = modifier
                .aspectRatio(1f)
                .fillMaxSize(),
            elevation = CardDefaults.elevatedCardElevation()
    ) {
        GlideImage(
                model = genre,
                contentDescription = genre.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                transition = CrossFade,
        ) {
            it.disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        }
    }
}