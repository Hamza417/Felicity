package app.simple.felicity.compose.commons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

@Composable
fun AnimatedItem(
        index: Int,
        totalAnimationDurationMillis: Int = 350,
        staggerPercent: Float = 0.15f,
        content: @Composable () -> Unit
) {
    val delayMillis = (index * totalAnimationDurationMillis * staggerPercent).toInt()
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible.value = true
    }

    AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(animationSpec = tween(totalAnimationDurationMillis)) +
                    slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(totalAnimationDurationMillis)
                    ) +
                    scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(totalAnimationDurationMillis)
                    ),
            exit = ExitTransition.None
    ) {
        content()
    }
}
