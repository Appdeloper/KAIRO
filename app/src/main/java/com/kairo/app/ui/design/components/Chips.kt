package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.data.local.Role as LaneRole
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.MinTouchTarget
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke
import com.kairo.app.ui.design.laneStyle

/** Tones for pills and banners. MOMENT is amber and reserved for the single key highlight. */
enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, ERROR, MOMENT }

@Composable
fun toneColor(tone: Tone): Color = with(KairoTheme.colors) {
    when (tone) {
        Tone.NEUTRAL -> textSecondary
        Tone.PRIMARY -> primary
        Tone.SUCCESS -> success
        Tone.WARNING -> warning
        Tone.ERROR -> error
        Tone.MOMENT -> moment
    }
}

/** A lane label: coloured dot plus the lane name, so colour is never the only signal. */
@Composable
fun RoleChip(
    role: LaneRole?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    label: String = role?.name.orEmpty(),
) {
    val lane = laneStyle(role)
    val colors = KairoTheme.colors
    Row(
        modifier
            .then(if (onClick != null) Modifier.defaultMinSize(minHeight = MinTouchTarget) else Modifier)
            .clip(Radius.full)
            .background(if (selected) lane.tint else colors.surface2)
            .border(Stroke.hairline, if (selected) lane.color else colors.outline, Radius.full)
            .then(if (onClick != null) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs + Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(Radius.full).background(lane.color))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = KairoTheme.type.labelMedium, color = if (selected) colors.textPrimary else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small status label, optionally with an icon so the meaning survives without colour. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL, icon: ImageVector? = null) {
    val color = toneColor(tone)
    Row(
        modifier
            .clip(Radius.full)
            .background(KairoTheme.colors.tint(color, 0.16f))
            .padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = KairoTheme.type.labelMedium, color = color, maxLines = 1)
    }
}

/** Two to four equal options in one rounded track (Timetable | Tasks, Day | Week). */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = KairoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.full)
            .background(colors.surface1)
            .border(Stroke.hairline, colors.outline, Radius.full)
            .padding(Spacing.xs)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(Radius.full)
                    .then(if (selected) Modifier.background(colors.surface3).border(Stroke.hairline, colors.outlineStrong, Radius.full) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = KairoTheme.type.labelLarge,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                )
            }
        }
    }
}

/** A selectable pill for small option sets (durations, days). */
@Composable
fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val colors = KairoTheme.colors
    Box(
        modifier
            .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .clip(Radius.full)
            .background(if (selected) colors.tint(colors.primary, 0.2f) else colors.surface2)
            .border(Stroke.hairline, if (selected) colors.primary else colors.outline, Radius.full)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KairoTheme.type.labelLarge, color = if (selected) colors.textPrimary else colors.textSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun ChipsPreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PreviewData.roles.forEachIndexed { i, r -> RoleChip(r, selected = i == 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill("Armed", tone = Tone.SUCCESS, icon = Icons.Outlined.CheckCircle)
                StatusPill("Needs a fix", tone = Tone.WARNING, icon = Icons.Outlined.WarningAmber)
                StatusPill("Offline brief", tone = Tone.NEUTRAL, icon = Icons.Outlined.CloudOff)
                StatusPill("Now", tone = Tone.MOMENT)
            }
            SegmentedControl(listOf("Timetable", "Tasks"), 0, {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChoicePill("15", false, {}); ChoicePill("25", true, {}); ChoicePill("45", false, {})
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
