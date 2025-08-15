package app.simple.felicity.compose.nav

import android.content.Context
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.SharedPreferences

private const val ANIMATION_DURATION = 400
private const val DELAY = 100

private var predictiveBackCallback: OnBackInvokedCallback? = null

@Composable
fun FelicityNavigation(context: Context) {

    val navController = rememberNavController()
    val disableAnimations = remember { mutableStateOf(AccessibilityPreferences.isAnimationReduced()) }
    val predictiveBack = remember { mutableStateOf(AccessibilityPreferences.isPredictiveBack()) }
    val startDestination = Routes.HOME // if (isSetupComplete(context)) Routes.HOME else Routes.SETUP

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                AccessibilityPreferences.REDUCE_ANIMATIONS -> {
                    disableAnimations.value = AccessibilityPreferences.isAnimationReduced()
                }
                AccessibilityPreferences.PREDICTIVE_BACK -> {
                    predictiveBack.value = AccessibilityPreferences.isPredictiveBack()
                }
            }
        }

        val prefs = SharedPreferences.getSharedPreferences()
        prefs.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    DisposableEffect(predictiveBack.value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context as ComponentActivity
            if (predictiveBack.value) {
                enablePredictiveBack(activity)
                onDispose {
                    // No-op, already unregistered in enablePredictiveBack
                }
            } else {
                disablePredictiveBack(activity) {
                    activity.onBackPressedDispatcher.onBackPressed()
                }
                onDispose {
                    enablePredictiveBack(activity) // Unregister on dispose
                }
            }
        } else {
            onDispose { }
        }
    }

    NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { if (disableAnimations.value) EnterTransition.None else scaleIntoContainer() },
            exitTransition = { if (disableAnimations.value) ExitTransition.None else scaleOutOfContainer(direction = ScaleTransitionDirection.INWARDS) },
            popEnterTransition = { if (disableAnimations.value) EnterTransition.None else scaleIntoContainer(direction = ScaleTransitionDirection.OUTWARDS) },
            popExitTransition = { if (disableAnimations.value) ExitTransition.None else scaleOutOfContainer() },
    ) {

    }
}

fun scaleIntoContainer(
        direction: ScaleTransitionDirection = ScaleTransitionDirection.INWARDS,
        initialScale: Float = if (direction == ScaleTransitionDirection.OUTWARDS) 0.9f else 1.1f
): EnterTransition {
    return scaleIn(
            animationSpec = tween(ANIMATION_DURATION, delayMillis = DELAY),
            initialScale = initialScale
    ) + fadeIn(animationSpec = tween(ANIMATION_DURATION, delayMillis = DELAY))
}

fun scaleOutOfContainer(
        direction: ScaleTransitionDirection = ScaleTransitionDirection.OUTWARDS,
        targetScale: Float = if (direction == ScaleTransitionDirection.INWARDS) 0.9f else 1.1f
): ExitTransition {
    return scaleOut(
            animationSpec = tween(
                    durationMillis = ANIMATION_DURATION,
                    delayMillis = DELAY
            ), targetScale = targetScale
    ) + fadeOut(tween(delayMillis = DELAY))
}

fun disablePredictiveBack(activity: ComponentActivity, onBack: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackCallback == null) {
        predictiveBackCallback = OnBackInvokedCallback {
            onBack()
        }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                predictiveBackCallback!!
        )
    }
}

fun enablePredictiveBack(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackCallback != null) {
        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(predictiveBackCallback!!)
        predictiveBackCallback = null
    }
}

enum class ScaleTransitionDirection {
    INWARDS,
    OUTWARDS
}