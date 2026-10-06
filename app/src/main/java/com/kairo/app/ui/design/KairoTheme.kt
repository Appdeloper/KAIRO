package com.kairo.app.ui.design

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Dark only, by design: KAIRO is used at 7 am in bed and 1 am at a desk. Material components are
 * mapped onto the KAIRO roles so switches, fields and sliders come out on-brand without per-use
 * overrides, and the default content colour is soft white, so text can never fall back to black.
 */
@Composable
fun KairoTheme(content: @Composable () -> Unit) {
    val colors = DarkColorRoles
    val scheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.tint(colors.primary, 0.22f),
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.primaryEnd,
        onSecondary = colors.textPrimary,
        secondaryContainer = colors.surface3,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.moment,
        onTertiary = colors.onPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface1,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.primary,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.surface1,
        surfaceContainer = colors.surface1,
        surfaceContainerHigh = colors.surface2,
        surfaceContainerHighest = colors.surface3,
        surfaceBright = colors.surface3,
        surfaceDim = colors.background,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.background,
        inversePrimary = colors.primaryEnd,
        outline = colors.outlineStrong,
        outlineVariant = colors.outline,
        error = colors.error,
        onError = colors.onPrimary,
        errorContainer = colors.tint(colors.error, 0.2f),
        onErrorContainer = colors.textPrimary,
        scrim = colors.background,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.sm),
        small = RoundedCornerShape(Radius.sm),
        medium = RoundedCornerShape(Radius.md),
        large = RoundedCornerShape(Radius.lg),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CompositionLocalProvider(
        LocalKairoColors provides colors,
        LocalReducedMotion provides readReducedMotion(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = KairoTypography, shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}

/** Entry point for tokens: `KairoTheme.colors.primary`, `KairoTheme.type.titleLarge`, `KairoTheme.numbers.hero`. */
object KairoTheme {
    val colors: KairoColorRoles
        @Composable @ReadOnlyComposable get() = LocalKairoColors.current
    val type
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val numbers: KairoNumberStyles get() = KairoNumbers
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
