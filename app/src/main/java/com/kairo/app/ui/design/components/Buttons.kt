package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.MinTouchTarget
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke

private val ButtonHeight = 52.dp

/** The one main action on a screen: cyan → blue gradient pill, dark ink label. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = KairoTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = ButtonHeight)
            .clip(Radius.full)
            .background(if (enabled) colors.primaryGradientHorizontal else androidx.compose.ui.graphics.SolidColor(colors.surface3))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, icon, if (enabled) colors.onPrimary else colors.textTertiary)
    }
}

/** A quieter action next to a primary one: outlined glass pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = KairoTheme.colors.textPrimary,
) {
    val colors = KairoTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = ButtonHeight)
            .clip(Radius.full)
            .background(colors.surface2)
            .border(Stroke.hairline, colors.outlineStrong, Radius.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text, icon, tint)
    }
}

/** Low-emphasis action (Cancel, Skip, Later). */
@Composable
fun KairoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = KairoTheme.colors.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) { Text(text, style = KairoTheme.type.labelLarge) }
}

/** Icon button with a guaranteed 48 dp touch target. [filled] gives the round gradient style used for the mic. */
@Composable
fun KairoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = KairoTheme.colors.textSecondary,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    if (filled) {
        val colors = KairoTheme.colors
        Box(
            modifier
                .size(MinTouchTarget)
                .clip(Radius.full)
                .background(colors.primaryGradient)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = contentDescription, tint = colors.onPrimary) }
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(MinTouchTarget),
            colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        ) { Icon(icon, contentDescription = contentDescription) }
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = KairoTheme.type.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun ButtonsPreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrimaryButton("Apply", {})
            PrimaryButton("Add task", {}, icon = Icons.Outlined.Add)
            PrimaryButton("Disabled", {}, enabled = false)
            SecondaryButton("Start focus", {})
            Row(verticalAlignment = Alignment.CenterVertically) {
                KairoTextButton("Cancel", {})
                KairoIconButton(Icons.Outlined.Mic, "Speak", {})
                KairoIconButton(Icons.Outlined.Mic, "Speak", {}, filled = true)
            }
        }
    }
}
