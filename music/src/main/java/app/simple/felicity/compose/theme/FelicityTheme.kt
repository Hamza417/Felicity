package app.simple.felicity.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalFelicityColors = staticCompositionLocalOf<FelicityColors> {
    error("No AppColors provided")
}

val LocalFelicityTypography = staticCompositionLocalOf<FelicityTypography> {
    error("No AppTypography provided!")
}

@Composable
fun FelicityTheme(
        colors: FelicityColors = LightThemeColors,
        typography: FelicityTypography = NotoSansDefault,
        content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFelicityColors provides colors, LocalFelicityTypography provides typography) {
        content()
    }
}
