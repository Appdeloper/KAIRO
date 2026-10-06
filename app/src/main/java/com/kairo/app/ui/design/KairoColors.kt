package com.kairo.app.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Raw brand values. Screens never use these directly: they read the named roles in [KairoColorRoles]
 * through `KairoTheme.colors`, so a palette change happens in one place.
 * Contrast ratios for every text/icon pairing are listed in docs/DESIGN_SYSTEM.md.
 */
internal object Palette {
    val Ink = Color(0xFF05070F)
    val Navy1 = Color(0xFF0C1222)
    val Navy2 = Color(0xFF121A30)
    val Navy3 = Color(0xFF1A2440)
    val Line = Color(0xFF2A3556)
    val LineStrong = Color(0xFF3B4874)

    val Cyan = Color(0xFF00E5FF)
    val Blue = Color(0xFF4D7CFF)
    val DeepInk = Color(0xFF04121C)

    val SoftWhite = Color(0xFFF2F8FF)
    val Mist = Color(0xFFAAB6D3)
    val Slate = Color(0xFF8590B0)

    val Mint = Color(0xFF4ADE80)
    val Coral = Color(0xFFFF9466)
    val Rose = Color(0xFFFF6B81)
    val Amber = Color(0xFFFFB020)
}

@Immutable
data class KairoColorRoles(
    val background: Color,
    /** Glass surface levels, each a little lighter and bluer: cards (1), raised cards and sheets (2), inputs and pressed states (3). */
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    /** Hairlines and dividers. Decorative only: never the only way state is shown. */
    val outline: Color,
    val outlineStrong: Color,
    val primary: Color,
    val primaryEnd: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    /** Amber: only for "the right moment" (now marker, the one key highlight on a screen, the logo spark). */
    val moment: Color,
) {
    val primaryGradient: Brush get() = Brush.linearGradient(listOf(primary, primaryEnd))
    val primaryGradientHorizontal: Brush get() = Brush.horizontalGradient(listOf(primary, primaryEnd))

    /** A faint tint of any accent for chips, pills and banners, solid so text contrast stays predictable. */
    fun tint(accent: Color, amount: Float = 0.16f): Color = lerpOver(surface1, accent, amount)
}

internal fun lerpOver(base: Color, accent: Color, amount: Float) = Color(
    red = base.red + (accent.red - base.red) * amount,
    green = base.green + (accent.green - base.green) * amount,
    blue = base.blue + (accent.blue - base.blue) * amount,
    alpha = 1f,
)

val DarkColorRoles = KairoColorRoles(
    background = Palette.Ink,
    surface1 = Palette.Navy1,
    surface2 = Palette.Navy2,
    surface3 = Palette.Navy3,
    outline = Palette.Line,
    outlineStrong = Palette.LineStrong,
    primary = Palette.Cyan,
    primaryEnd = Palette.Blue,
    onPrimary = Palette.DeepInk,
    textPrimary = Palette.SoftWhite,
    textSecondary = Palette.Mist,
    textTertiary = Palette.Slate,
    success = Palette.Mint,
    warning = Palette.Coral,
    error = Palette.Rose,
    moment = Palette.Amber,
)

val LocalKairoColors = staticCompositionLocalOf { DarkColorRoles }
