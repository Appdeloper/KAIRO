package com.kairo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object KairoColors {
    val Background = Color(0xFF07070B)
    val Surface = Color(0xFF0F0F16)
    val SurfaceHigh = Color(0xFF181822)
    val SurfaceHighest = Color(0xFF22222E)
    val Outline = Color(0xFF2C2C3A)
    val OnBackground = Color(0xFFECECF4)
    val Muted = Color(0xFF9A9AB0)

    val NeonCyan = Color(0xFF00F0FF)
    val NeonMagenta = Color(0xFFFF2BD6)
    val NeonLime = Color(0xFFB6FF3B)
    val NeonRed = Color(0xFFFF4D6D)
}

// KAIRO is dark-only by design: it's used at 7am in bed and 1am at a desk, and neon reads best on near-black.
private val ColorScheme = darkColorScheme(
    primary = KairoColors.NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003A40),
    onPrimaryContainer = KairoColors.NeonCyan,
    secondary = KairoColors.NeonMagenta,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3D0A33),
    onSecondaryContainer = KairoColors.NeonMagenta,
    tertiary = KairoColors.NeonLime,
    onTertiary = Color.Black,
    background = KairoColors.Background,
    onBackground = KairoColors.OnBackground,
    surface = KairoColors.Surface,
    onSurface = KairoColors.OnBackground,
    surfaceVariant = KairoColors.SurfaceHigh,
    onSurfaceVariant = KairoColors.Muted,
    surfaceContainerLowest = KairoColors.Background,
    surfaceContainerLow = KairoColors.Surface,
    surfaceContainer = KairoColors.Surface,
    surfaceContainerHigh = KairoColors.SurfaceHigh,
    surfaceContainerHighest = KairoColors.SurfaceHighest,
    outline = KairoColors.Outline,
    outlineVariant = KairoColors.Outline,
    error = KairoColors.NeonRed,
    onError = Color.Black,
)

private val KairoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KairoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, shapes = KairoShapes, content = content)
}
