package com.kairo.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.Stroke

enum class TimelineState { UPCOMING, NOW, DONE, SKIPPED, FOCUS }

/**
 * One timeline row: a time column, a lane rail, and a glass card. The lane shows as colour *and* as
 * its name in [laneLabel]; done and skipped are struck through *and* carry a [stateText] pill.
 */
@Composable
fun TimelineItem(
    startTime: String,
    endTime: String?,
    title: String,
    laneColor: Color,
    modifier: Modifier = Modifier,
    laneLabel: String? = null,
    detail: String? = null,
    nextStep: String? = null,
    state: TimelineState = TimelineState.UPCOMING,
    stateText: String? = null,
    locked: Boolean = false,
    lockedDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = KairoTheme.colors
    val dim = state == TimelineState.DONE || state == TimelineState.SKIPPED
    val struck = dim
    val elevation = if (state == TimelineState.FOCUS) Elevation.GLOW else Elevation.LOW
    // At large font sizes the side time column would squeeze the card, so times go above it instead.
    val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
    val card: @Composable RowScope.() -> Unit = {
        Row(
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.xs)
                .glass(colors.surface1, Radius.medium, elevation, laneColor)
                .then(if (state == TimelineState.FOCUS) Modifier.border(Stroke.regular, laneColor, Radius.medium) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).alpha(if (dim) 0.72f else 1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    title,
                    style = KairoTheme.type.titleMedium,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    color = if (dim) colors.textSecondary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (laneLabel != null) {
                        Box(Modifier.size(8.dp).clip(Radius.full).background(laneColor))
                        Text(laneLabel, style = KairoTheme.type.labelMedium, color = colors.textSecondary, maxLines = 1)
                    }
                    if (detail != null) Text(detail, style = KairoTheme.type.labelMedium, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (stateText != null && state != TimelineState.UPCOMING) {
                    StatusPill(
                        stateText,
                        tone = when (state) {
                            TimelineState.FOCUS -> Tone.PRIMARY
                            TimelineState.DONE -> Tone.SUCCESS
                            TimelineState.NOW -> Tone.MOMENT
                            else -> Tone.NEUTRAL
                        },
                        icon = if (state == TimelineState.FOCUS) Icons.Outlined.Bolt else null,
                    )
                }
                if (nextStep != null) {
                    Text(nextStep, style = KairoTheme.type.bodySmall, color = laneColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (locked) {
                Icon(Icons.Outlined.Lock, contentDescription = lockedDescription, tint = colors.textTertiary, modifier = Modifier.padding(horizontal = Spacing.md).size(18.dp))
            }
            trailing?.invoke(this)
        }
    }
    val semanticsModifier = modifier.fillMaxWidth().semantics { if (stateText != null) stateDescription = stateText }
    if (stacked) {
        Column(semanticsModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                if (endTime != null) "$startTime – $endTime" else startTime,
                style = KairoTheme.numbers.small,
                color = if (dim) colors.textTertiary else colors.textSecondary,
            )
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.fillMaxHeight().width(Stroke.strong).clip(Radius.full).background(laneColor.copy(alpha = if (dim) 0.35f else 0.9f)))
                Spacer(Modifier.width(Spacing.sm))
                card()
            }
        }
    } else {
        Row(semanticsModifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            Column(Modifier.widthIn(min = 64.dp).padding(top = Spacing.md), horizontalAlignment = Alignment.End) {
                Text(startTime, style = KairoTheme.numbers.small, color = if (dim) colors.textTertiary else colors.textPrimary)
                if (endTime != null) Text(endTime, style = KairoTheme.numbers.small, color = colors.textTertiary)
            }
            Spacer(Modifier.width(Spacing.md))
            Box(Modifier.fillMaxHeight().width(Stroke.strong).clip(Radius.full).background(laneColor.copy(alpha = if (dim) 0.35f else 0.9f)))
            Spacer(Modifier.width(Spacing.md))
            card()
        }
    }
}

private const val STACK_FONT_SCALE = 1.3f

/** The amber "now" line across the timeline. Amber appears here because this is the right moment. */
@Composable
fun NowMarker(label: String, modifier: Modifier = Modifier) {
    val amber = KairoTheme.colors.moment
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KairoTheme.numbers.small, color = amber, modifier = Modifier.widthIn(min = 64.dp))
        Spacer(Modifier.width(Spacing.md - 3.dp))
        Box(Modifier.size(8.dp).clip(Radius.full).background(amber))
        Box(Modifier.weight(1f).height(Stroke.regular).background(amber.copy(alpha = 0.7f)))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun TimelinePreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TimelineItem("9:00", "10:00", "DBMS lecture", Color(0xFF38D9F5), laneLabel = "College", detail = "LH-204", locked = true, state = TimelineState.DONE, stateText = "Done")
            NowMarker("10:42")
            TimelineItem("11:00", "13:00", "OS lab", Color(0xFF38D9F5), laneLabel = "College", state = TimelineState.SKIPPED, stateText = "Skipped today", locked = true)
            TimelineItem("16:00", "16:45", "Edit reel #12", Color(0xFFFF6FB7), laneLabel = "Content", state = TimelineState.FOCUS, stateText = "Focusing · 17:32 left", nextStep = "Next: add captions")
            TimelineItem("20:00", "21:30", "Intern report", Color(0xFF9B8CFF), laneLabel = "Intern")
        }
    }
}
