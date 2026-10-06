package com.kairo.app.ui.alarms

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.OrbGlyph
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.glass
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun AlarmRingScreen(
    clock: LocalTime,
    label: String,
    firstItem: String?,
    snoozeMinutes: Int,
    snoozesLeft: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KairoTheme.colors
    Column(
        modifier
            .fillMaxSize()
            // Soft navy glow from the top, never a white blast in a dark room.
            .background(Brush.verticalGradient(listOf(colors.surface2, colors.background)))
            .safeDrawingPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Spacer(Modifier.height(Spacing.xl))
            // Shrinks to fit (12-hour times with AM/PM, or 200% font) instead of clipping the digits.
            BasicText(
                clock.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                modifier = Modifier.fillMaxWidth(),
                style = KairoTheme.numbers.hero.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = RING_TIME_MIN_SIZE, maxFontSize = RING_TIME_SIZE),
            )
            Text(
                label.ifBlank { stringResource(R.string.alarm_default_label) },
                style = KairoTheme.type.headlineMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        // Static orb: the ring screen spends its battery on staying awake, not on shaders.
        OrbGlyph(size = 150.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            firstItem?.let {
                GlassCard(contentPadding = PaddingValues(Spacing.lg)) {
                    Text(stringResource(R.string.alarm_first_up_label).uppercase(), style = KairoTheme.type.labelSmall, color = colors.textSecondary)
                    Text(it, style = KairoTheme.type.titleLarge)
                }
            }
            SecondaryButton(
                if (snoozesLeft > 0) {
                    pluralStringResource(R.plurals.alarm_snooze_left, snoozesLeft, snoozeMinutes, snoozesLeft)
                } else {
                    stringResource(R.string.alarm_no_snoozes)
                },
                onClick = onSnooze,
                enabled = snoozesLeft > 0,
                icon = Icons.Outlined.Snooze,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
            SlideToDismiss(onDismiss)
        }
    }
}

private val RING_TIME_SIZE = 96.sp
private val RING_TIME_MIN_SIZE = 40.sp
private const val DISMISS_THRESHOLD = 0.8f

/** A deliberate slide, so a pocket or pillow can't dismiss the alarm. TalkBack can still activate it. */
@Composable
private fun SlideToDismiss(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val colors = KairoTheme.colors
    val dismissLabel = stringResource(R.string.alarm_slide_to_dismiss)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .glass(colors.surface2, Radius.full, Elevation.RAISED)
            .semantics { onClick(label = dismissLabel) { onDismiss(); true } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = TRACK_HEIGHT - 8.dp
        val maxPx = with(LocalDensity.current) { (maxWidth - thumb - 8.dp).toPx() }
        Text(dismissLabel, Modifier.fillMaxWidth().padding(start = thumb), textAlign = TextAlign.Center, style = KairoTheme.type.titleMedium, color = colors.textSecondary)
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(Radius.full)
                .background(colors.primaryGradient)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value >= maxPx * DISMISS_THRESHOLD) {
                                    offset.animateTo(maxPx)
                                    onDismiss()
                                } else {
                                    offset.animateTo(0f)
                                }
                            }
                        },
                    ) { _, drag -> scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, maxPx)) } }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

private val TRACK_HEIGHT = 88.dp

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 851)
@Composable
private fun AlarmRingScreenPreview() {
    KairoTheme {
        AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 851)
@Composable
private fun AlarmRingNoSnoozePreview() {
    KairoTheme {
        AlarmRingScreen(LocalTime.of(7, 15), "", null, 5, 0, {}, {})
    }
}
