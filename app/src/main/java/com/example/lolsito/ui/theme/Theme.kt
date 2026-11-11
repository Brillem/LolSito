package com.example.lolsito.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The one scheme of the app: black background, very dark gray surfaces, off-white text and red as
 * the only accent. [androidx.compose.material3.ColorScheme.inversePrimary] carries [RedText], the
 * red to use for accent text on dark surfaces, where [Red] itself is too dim for small text.
 */
private val LolSitoColorScheme = darkColorScheme(
    primary = Red,
    onPrimary = OnRed,
    primaryContainer = RedContainer,
    onPrimaryContainer = OnRedContainer,
    inversePrimary = RedText,
    secondary = TextSecondary,
    onSecondary = Black,
    secondaryContainer = RedContainer,
    onSecondaryContainer = OnRedContainer,
    tertiary = RedText,
    onTertiary = Black,
    tertiaryContainer = RedContainer,
    onTertiaryContainer = OnRedContainer,
    background = Black,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Red,
    inverseSurface = TextPrimary,
    inverseOnSurface = SurfaceContainer,
    error = ErrorRed,
    onError = Black,
    errorContainer = RedContainer,
    onErrorContainer = OnRedContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Black,
    surfaceBright = SurfaceBright,
    surfaceDim = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

/** Fixed dark theme: it ignores the system setting and Material dynamic color on purpose. */
@Composable
fun LolSitoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LolSitoColorScheme,
        typography = Typography,
        content = content,
    )
}
