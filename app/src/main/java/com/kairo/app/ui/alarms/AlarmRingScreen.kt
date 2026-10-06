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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.R
import com.kairo.app.ui.briefing.Orb
import com.kairo.app.ui.briefing.OrbState
import com.kairo.app.ui.design.KairoTheme
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
    Column(
        modifier
            .fillMaxSize()
            .background(KairoTheme.colors.background)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(32.dp))
            Text(
                clock.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = KairoTheme.numbers.hero,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                label.ifBlank { stringResource(R.string.alarm_default_label) },
                style = MaterialTheme.typography.headlineSmall,
                color = KairoTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
            firstItem?.let {
                Text(stringResource(R.string.alarm_first_item, it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Static orb: the ring screen spends its GPU budget on staying awake, not on shaders.
        Orb(OrbState.LISTENING, level = 0.6f, running = false, forceFallback = true, modifier = Modifier.size(180.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onSnooze, enabled = snoozesLeft > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(
                    if (snoozesLeft > 0) {
                        pluralStringResource(R.plurals.alarm_snooze_left, snoozesLeft, snoozeMinutes, snoozesLeft)
                    } else {
                        stringResource(R.string.alarm_no_snoozes)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            SlideToDismiss(onDismiss)
        }
    }
}

private const val DISMISS_THRESHOLD = 0.8f

/** A deliberate slide, so a pocket or pillow can't dismiss the alarm. TalkBack can still activate it. */
@Composable
private fun SlideToDismiss(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismissLabel = stringResource(R.string.alarm_slide_to_dismiss)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(36.dp))
            .semantics { onClick(label = dismissLabel) { onDismiss(); true } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 64.dp
        val maxPx = with(LocalDensity.current) { (maxWidth - thumb - 8.dp).toPx() }
        Text(dismissLabel, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .background(KairoTheme.colors.primary, CircleShape)
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
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KairoTheme.colors.onPrimary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 780)
@Composable
private fun AlarmRingScreenPreview() {
    KairoTheme {
        AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 780)
@Composable
private fun AlarmRingNoSnoozePreview() {
    KairoTheme {
        AlarmRingScreen(LocalTime.of(7, 15), "", null, 5, 0, {}, {})
    }
}
