package app.simple.felicity.compose.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

val LocalFelicityColors = staticCompositionLocalOf<FelicityColors> {
    error("No AppColors provided")
}

val LocalFelicityTypography = staticCompositionLocalOf<FelicityTypography> {
    error("No AppTypography provided!")
}

val LocalBarsSize = staticCompositionLocalOf<BarSize> {
    error("No BarsSize provided!")
}

@Composable
fun FelicityTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        colors: FelicityColors = LightThemeColors,
        typography: FelicityTypography = NotoSansDefault,
        content: @Composable () -> Unit
) {
    var statusBarHeight by remember { mutableIntStateOf(0) }
    var navigationBarHeight by remember { mutableIntStateOf(0) }

    statusBarHeight = WindowInsetsCompat.toWindowInsetsCompat(
            LocalView.current.rootWindowInsets).getInsets(WindowInsetsCompat.Type.statusBars()).top
    navigationBarHeight = WindowInsetsCompat.toWindowInsetsCompat(
            LocalView.current.rootWindowInsets).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

    val statusBarHeightPx = statusBarHeight
    val statusBarHeightDp = with(LocalDensity.current) { statusBarHeightPx.toDp() }
    val navigationBarHeightPx = navigationBarHeight
    val navigationBarHeightDp = with(LocalDensity.current) { navigationBarHeightPx.toDp() }

    val barSize = BarSize(statusBarHeightDp, navigationBarHeightDp)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme.not()
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = darkTheme.not()
        }
    }

    CompositionLocalProvider(
            LocalFelicityColors provides colors,
            LocalFelicityTypography provides typography,
            LocalBarsSize provides barSize
    ) {
        content()
    }
}

data class BarSize(
        val statusBarHeight: Dp = 0.dp,
        val navigationBarHeight: Dp = 0.dp
)