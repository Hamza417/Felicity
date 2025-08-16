package app.simple.felicity.compose.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlin.math.absoluteValue
import kotlin.math.sign

@SuppressLint("RestrictedApi", "FrequentlyChangingValue")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnappingCarousel(
        items: List<Song>,
        modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(listState)

    LazyRow(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(-50.dp), // negative for overlap
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(items) { index, item ->
            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
            val viewportCenter = listState.layoutInfo.viewportStartOffset +
                    (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) / 2
            val itemCenter = itemInfo?.let { it.offset + it.size / 2 } ?: 0
            val rawDistance = (viewportCenter - itemCenter).toFloat()
            val normalized = (rawDistance / viewportCenter).coerceIn(-1f, 1f)

            // Cubic ease-out decay for strong center stage effect
            val easeOutCubic = 1f - (1f - normalized.absoluteValue).let { it * it * it }
            val decayedDistance = normalized.sign * easeOutCubic

            val maxRotation = 60f
            val rotationY = maxRotation * decayedDistance

            val maxSpread = 60f
            val featuredGap = 80f

            val spreadFactor = decayedDistance.absoluteValue
            val gapFactor = 1f - decayedDistance.absoluteValue

            val translationX = decayedDistance * maxSpread * spreadFactor + featuredGap * gapFactor

            // Scale for center prominence
            val minScale = 0.85f
            val scale = lerp(minScale, 1f, 1f - decayedDistance.absoluteValue)

            Box(
                    modifier = Modifier
                        .graphicsLayer {
                            this.rotationY = rotationY
                            this.translationX = translationX
                            this.scaleX = scale
                            this.scaleY = scale
                            cameraDistance = 32 * density
                        }
                        .size(250.dp)
            ) {
                AlbumCover(song = item)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AlbumCover(song: Song) {
    // Placeholder for album cover composable
    // Replace with actual implementation to display album cover image and title
    Box(modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth()) {
        GlideImage(
                model = song,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(
                    )
                    .graphicsLayer {
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        clip = true
                    }

        )
    }
}
