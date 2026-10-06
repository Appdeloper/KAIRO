package com.kairo.app.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Motion
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke

/** Circular progress with the brand gradient (or a lane colour) and a soft glow on the arc. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = Stroke.ring,
    color: Color? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = KairoTheme.colors
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        Motion.orInstant(KairoTheme.reducedMotion, Motion.long()),
        label = "ring",
    )
    val brush = if (color != null) Brush.sweepGradient(listOf(color, color)) else Brush.sweepGradient(listOf(colors.primary, colors.primaryEnd, colors.primary))
    Box(
        modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(colors.surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = DrawStroke(stroke))
            if (animated > 0f) {
                rotate(-90f) {
                    drawArc((color ?: colors.primary).copy(alpha = 0.18f), 0f, 360f * animated, false, Offset(inset, inset), arcSize, style = DrawStroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, 360f * animated, false, Offset(inset, inset), arcSize, style = DrawStroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun ProgressRingPreview() {
    KairoTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ProgressRing(0.33f) { Text("1/3", style = KairoTheme.numbers.medium) }
            ProgressRing(0.7f, size = 120.dp, color = Color(0xFFFF6FB7)) { Text("17:32", style = KairoTheme.numbers.medium) }
        }
    }
}
