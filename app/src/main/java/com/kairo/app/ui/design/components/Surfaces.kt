package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke

/**
 * A navy glass surface: a solid fill (so text contrast is exact), a hairline whose top edge is
 * brighter (light falling on glass), and at [Elevation.GLOW] a soft halo in [glowColor].
 */
fun Modifier.glass(
    fill: Color,
    shape: Shape,
    elevation: Elevation = Elevation.LOW,
    glowColor: Color = Color.Unspecified,
): Modifier {
    val withGlow = if (elevation.glowAlpha > 0f && glowColor != Color.Unspecified) {
        drawBehind {
            val r = size.maxDimension * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glowColor.copy(alpha = elevation.glowAlpha), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = r,
                ),
                radius = r,
            )
        }
    } else {
        this
    }
    val edge = if (elevation.highlightAlpha > 0f) {
        Modifier.border(
            Stroke.hairline,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = elevation.highlightAlpha), Color.White.copy(alpha = elevation.highlightAlpha * 0.2f)),
            ),
            shape,
        )
    } else {
        Modifier
    }
    return withGlow.clip(shape).background(fill, shape).then(edge)
}

/** Glass level: 1 for content cards, 2 for raised cards and sheets, 3 for inputs and nested items. */
enum class GlassLevel { ONE, TWO, THREE }

@Composable
fun glassFill(level: GlassLevel): Color = when (level) {
    GlassLevel.ONE -> KairoTheme.colors.surface1
    GlassLevel.TWO -> KairoTheme.colors.surface2
    GlassLevel.THREE -> KairoTheme.colors.surface3
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.ONE,
    elevation: Elevation = Elevation.LOW,
    glowColor: Color = KairoTheme.colors.primary,
    fill: Color = glassFill(level),
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(fill, Radius.large, elevation, glowColor)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun GlassCardPreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GlassCard { Text("Level 1 card", style = KairoTheme.type.titleMedium) }
            GlassCard(level = GlassLevel.TWO, elevation = Elevation.RAISED) { Text("Level 2, raised") }
            GlassCard(level = GlassLevel.TWO, elevation = Elevation.GLOW) { Text("Glowing card") }
        }
    }
}
