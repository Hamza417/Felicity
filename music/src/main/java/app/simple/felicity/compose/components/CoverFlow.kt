package app.simple.felicity.compose.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Public state holder so you can programmatically scroll/snap if you want.
 */
@Stable
class CoverFlowState internal constructor(
        initialIndex: Int,
        private val stridePx: Float,
        private val itemCountProvider: () -> Int,
        private val decay: DecayAnimationSpec<Float>
) {
    // "scroll" means how far the center has moved in item strides.
    // 0f = first item centered; 1f = second item centered; etc.
    private val _scroll = Animatable(initialIndex.toFloat())
    internal val anim: Animatable<Float, AnimationVector1D> get() = _scroll

    val index: Int get() = _scroll.value.roundToInt().coerceIn(0, max(0, itemCountProvider() - 1))
    val rawScroll: Float get() = _scroll.value

    suspend fun snapTo(index: Int) {
        _scroll.snapTo(index.coerceIn(0, itemCountProvider() - 1).toFloat())
    }

    suspend fun animateTo(index: Int) {
        _scroll.animateTo(
                index.coerceIn(0, itemCountProvider() - 1).toFloat(),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
        )
    }

    suspend fun dragBy(deltaPx: Float) {
        // convert pixel delta to "stride units"
        val deltaInStrides = deltaPx / stridePx
        _scroll.snapTo((_scroll.value - deltaInStrides).coerceIn(0f, max(0, itemCountProvider() - 1).toFloat()))
    }

    suspend fun fling(initialVelocityPxPerSec: Float) {
        // Use the passed-in decay instance
        val velStridesPerSec = initialVelocityPxPerSec / stridePx
        val target = decay.calculateTargetValue(_scroll.value, -velStridesPerSec)
        val clamped = target.coerceIn(0f, max(0, itemCountProvider() - 1).toFloat())
        val nearestIndex = clamped.roundToInt()
        _scroll.animateTo(
                nearestIndex.toFloat(),
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.9f)
        )
    }
}

@Composable
fun rememberCoverFlowState(
        itemWidth: Dp,
        itemSpacing: Dp,
        itemCountProvider: () -> Int,
        initialIndex: Int = 0
): CoverFlowState {
    val density = LocalDensity.current
    val stridePx = with(density) { itemWidth.toPx() + itemSpacing.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    return remember(stridePx, itemCountProvider, decay) {
        CoverFlowState(initialIndex, stridePx, itemCountProvider, decay)
    }
}

/**
 * A raw Cover Flow implementation (no Lazy layouts).
 *
 * - Drag to scroll (custom pointer input).
 * - Fling with decay + snapping to the nearest item.
 * - 3D Y-rotation + scale falloff toward edges.
 * - Click/tap any item to snap it to center.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CoverFlow(
        modifier: Modifier = Modifier,
        itemCount: Int,
        itemWidth: Dp = 180.dp,
        itemSpacing: Dp = 18.dp,
        sideItemScaleMin: Float = 0.82f,
        maxRotationY: Float = 55f,         // degrees at edges
        perspectiveFactor: Float = 12f,    // larger = less perspective
        state: CoverFlowState = rememberCoverFlowState(itemWidth, itemSpacing, { itemCount }),
        itemContent: @Composable (index: Int, focused: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val spacingPx = with(density) { itemSpacing.toPx() }
    val stridePx = itemWidthPx + spacingPx
    val cameraDistance = with(density) { 8 * density.density * 100f } // sane default

    // interaction (drag/ fling)
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()

    fun distanceFromCenterFor(index: Int, centerX: Float, containerWidth: Int): Float {
        // Convert current scroll (in strides) to pixel offset
        val centerItemIndexF = state.rawScroll
        val indexDelta = index - centerItemIndexF
        // items are spaced by stride; indexDelta is how many strides away this item is from center
        return indexDelta
    }

    // Container + custom layout
    Box(
            modifier = modifier
                .clipToBounds()
                .pointerInput(itemCount, stridePx) {
                    detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                            },
                            onDragEnd = {
                                isDragging = false
                                val v = velocityTracker.calculateVelocity().x
                                scope.launch { state.fling(v) }
                            },
                            onDragCancel = {
                                isDragging = false
                                scope.launch {
                                    // Snap to nearest on cancel
                                    state.animateTo(state.rawScroll.roundToInt())
                                }
                            }
                    ) { change, drag ->
                        change.consume()
                        // drag.x is positive when moving right; we want content to follow the finger
                        scope.launch {
                            state.dragBy(drag.x)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                    }
                }
    ) {
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val centerX = width / 2f

            // We render a bounded window of items around the center to avoid composing everything.
            val centerIndex = state.rawScroll.roundToInt().coerceIn(0, max(0, itemCount - 1))
            val visibleRadius = 6 // how many items on each side we consider
            val start = max(0, centerIndex - visibleRadius)
            val end = min(itemCount - 1, centerIndex + visibleRadius)

            // Subcompose & measure visible items with their per-item transforms baked in:
            val placeables = (start..end).map { index ->
                val delta = index - state.rawScroll // negative -> item is left of center
                val absDelta = abs(delta)

                val t = min(1f, absDelta) // 0..1
                val scale = lerp(1f, sideItemScaleMin, t)
                val rotY = clamp(-delta * maxRotationY, -maxRotationY, maxRotationY)
                val alpha = lerp(1f, 0.45f, t * 0.9f)

                val xCenterForItem = centerX + (delta * stridePx)
                val itemLeft = (xCenterForItem - itemWidthPx / 2f)

                val composed = subcompose("item_$index") {
                    Box(
                            modifier = Modifier
                                .rawCoverFlowItemTransform(
                                        rotationY = rotY,
                                        cameraDistance = cameraDistance,
                                        perspectiveFactor = perspectiveFactor
                                )
                                .size(width = itemWidth, height = (itemWidth * 1.0f)) // square-ish covers
                                .pointerInput(index) {
                                    detectTapGestures(onTap = {
                                        // tap to center this item
                                        val target = index
                                        if (target != state.index) {
                                            scope.launch { state.animateTo(target) }
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                    ) {
                        itemContent(index, index == centerIndex)
                    }
                }.first().measure(
                        constraints.copy(minWidth = 0, minHeight = 0)
                )

                Triple(index, composed, itemLeft.roundToInt())
            }

            layout(width, height) {
                placeables.forEach { (_, placeable, left) ->
                    val x = left
                    val y = (height - placeable.height) / 2
                    placeable.placeRelative(x = x, y = y)
                }
            }
        }
    }
}

@SuppressLint("UnnecessaryComposedModifier")
private fun Modifier.rawCoverFlowItemTransform(
        rotationY: Float,
        cameraDistance: Float,
        perspectiveFactor: Float
): Modifier = composed {
    this.graphicsLayer {
        this.rotationY = rotationY
        // Apply perspective factor to camera distance
        this.cameraDistance = cameraDistance * perspectiveFactor
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun clamp(v: Float, minV: Float, maxV: Float) = max(minV, min(maxV, v))

// ---------- Demo / Preview ----------

@Composable
private fun DemoItem(index: Int, focused: Boolean) {
    val colors = listOf(
            0xFFEF5350, 0xFFAB47BC, 0xFF5C6BC0, 0xFF29B6F6, 0xFF26A69A,
            0xFF9CCC65, 0xFFFFEE58, 0xFFFFA726, 0xFFA1887F, 0xFF90A4AE
    ).map { Color(it) }
    val bg = colors[index % colors.size]
    Box(
            modifier = Modifier
                .background(bg),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = if (focused) "🎵 $index" else "$index",
                color = Color.Black,
                fontSize = if (focused) 24.sp else 18.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 300)
@Composable
private fun CoverFlowPreview() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val itemCount = 20
            val state = rememberCoverFlowState(
                    itemWidth = 160.dp,
                    itemSpacing = 16.dp,
                    itemCountProvider = { itemCount },
                    initialIndex = 4
            )
            CoverFlow(
                    modifier = Modifier.fillMaxSize(),
                    itemCount = itemCount,
                    itemWidth = 160.dp,
                    itemSpacing = 16.dp,
                    state = state
            ) { index, focused ->
                DemoItem(index, focused)
            }
        }
    }
}
