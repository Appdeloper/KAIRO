package com.kairo.app.ui.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing

/**
 * The brand orb in miniature: a cyan-to-blue sphere with a soft halo. Used for loading states, the
 * floating briefing button and empty-state illustrations. It breathes only when [animate] is true
 * and the system hasn't turned animations off; otherwise it's drawn once, static.
 */
@Composable
fun LoadingOrb(modifier: Modifier = Modifier, size: Dp = 56.dp, animate: Boolean = true, glow: Float = 1f) {
    val colors = KairoTheme.colors
    val moving = animate && !KairoTheme.reducedMotion
    val pulse = if (moving) {
        val transition = rememberInfiniteTransition(label = "orb")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = LinearEasing), RepeatMode.Reverse),
            label = "breath",
        )
        value
    } else {
        0.5f
    }
    Canvas(modifier.size(size)) { drawOrb(colors.primary, colors.primaryEnd, pulse, glow) }
}

/** Static orb for illustrations (empty states, About). */
@Composable
fun OrbGlyph(modifier: Modifier = Modifier, size: Dp = 72.dp) = LoadingOrb(modifier, size, animate = false)

private const val BREATH_MS = 1_800

internal fun DrawScope.drawOrb(core: Color, edge: Color, pulse: Float, glow: Float) {
    val r = size.minDimension / 2f * 0.62f * (0.96f + 0.06f * pulse)
    val haloR = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to core.copy(alpha = (0.28f + 0.14f * pulse) * glow),
            0.55f to edge.copy(alpha = 0.12f * glow),
            1f to Color.Transparent,
            center = center,
            radius = haloR,
        ),
        radius = haloR,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFE8FCFF),
            0.28f to core,
            0.8f to edge,
            1f to edge.copy(alpha = 0.85f),
            center = Offset(center.x - r * 0.25f, center.y - r * 0.3f),
            radius = r * 1.35f,
        ),
        radius = r,
    )
    drawCircle(color = Color.White.copy(alpha = 0.18f), radius = r, style = DrawStroke(width = r * 0.04f))
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun OrbsPreview() {
    KairoTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            LoadingOrb(size = 40.dp)
            LoadingOrb(size = 64.dp)
            OrbGlyph(size = 96.dp)
        }
    }
}
