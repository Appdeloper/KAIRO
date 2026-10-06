package com.kairo.app.ui.focus

import com.kairo.app.util.beta.Events
import com.kairo.app.util.beta.BetaEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationManagerCompat
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.local.Role
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.focus.FocusDurations
import com.kairo.app.domain.focus.FocusRules
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.Banner
import com.kairo.app.ui.design.components.BannerTone
import com.kairo.app.ui.design.components.ChoicePill
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.RoleChip
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.SheetFrame
import com.kairo.app.ui.design.components.SliderRow
import com.kairo.app.ui.design.rememberKairoHaptics

/** What a focus session is about: a task (linked, gets the next step) or a lecture block (label only). */
data class FocusTarget(val taskId: Long?, val title: String, val role: Role?, val startMinute: Int?, val endMinute: Int?) {
    companion object {
        fun from(entry: TimelineEntry) = FocusTarget(
            taskId = (entry as? TimelineEntry.TaskEntry)?.task?.id,
            title = entry.title,
            role = entry.role,
            startMinute = entry.startMinute,
            endMinute = entry.endMinute,
        )
    }
}

/** Same sheet style as the change preview: full height, no half-expanded state. */
@Composable
fun FocusStartSheet(
    target: FocusTarget,
    defaultMinutes: Int,
    running: FocusSession?,
    onStart: (minutes: Int) -> Unit,
    onOpenRunning: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notificationsOff = remember { !NotificationManagerCompat.from(context).areNotificationsEnabled() }
    androidx.compose.runtime.LaunchedEffect(Unit) { Events.record(BetaEvent.FOCUS_SHEET_OPEN) }
    KairoBottomSheet(onDismissRequest = onDismiss) {
        FocusStartContent(
            target = target,
            initialMinutes = FocusDurations.initialFor(target.startMinute, target.endMinute, defaultMinutes),
            running = running,
            onStart = onStart,
            onOpenRunning = onOpenRunning,
            onCancel = onDismiss,
            notificationsOff = notificationsOff,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusStartContent(
    target: FocusTarget,
    initialMinutes: Int,
    running: FocusSession?,
    onStart: (minutes: Int) -> Unit,
    onOpenRunning: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    notificationsOff: Boolean = false,
) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var custom by rememberSaveable { mutableStateOf(initialMinutes !in FocusDurations.CHIPS) }
    val haptics = rememberKairoHaptics()

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.focus_start_title), style = KairoTheme.type.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleChip(target.role)
            Spacer(Modifier.width(Spacing.sm))
            Text(target.title, style = KairoTheme.type.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SectionHeader(stringResource(R.string.focus_how_long))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FocusDurations.CHIPS.forEach { option ->
                ChoicePill(stringResource(R.string.focus_minutes, option), !custom && minutes == option, {
                    custom = false
                    minutes = option
                })
            }
            ChoicePill(stringResource(R.string.focus_custom), custom, { custom = true })
        }
        if (custom) {
            SliderRow(
                stringResource(R.string.focus_custom),
                stringResource(R.string.focus_minutes, minutes),
                minutes.toFloat(),
                { minutes = FocusDurations.snap(it) },
                valueRange = FocusRules.MIN_MINUTES.toFloat()..FocusRules.MAX_MINUTES.toFloat(),
            )
        }
        running?.let { RunningNotice(it, onOpenRunning) }
        if (notificationsOff) {
            Banner(BannerTone.WARNING, stringResource(R.string.focus_notifications_off))
        }
        Spacer(Modifier.height(Spacing.xs))
        PrimaryButton(
            stringResource(R.string.focus_start_button, minutes),
            onClick = {
                haptics.tick()
                onStart(minutes)
            },
            enabled = running == null,
            modifier = Modifier.fillMaxWidth(),
        )
        KairoTextButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), color = KairoTheme.colors.textSecondary)
    }
}

/** One session at a time: say so plainly and offer the running one instead of silently refusing. */
@Composable
private fun RunningNotice(running: FocusSession, onOpenRunning: () -> Unit) {
    Banner(
        BannerTone.INFO,
        stringResource(R.string.focus_already_running, running.blockLabel),
        actionLabel = stringResource(R.string.focus_open_running),
        onAction = onOpenRunning,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun FocusStartContentPreview() {
    KairoTheme {
        SheetFrame { FocusStartContent(
            target = FocusTarget(taskId = 2, title = "Edit reel #12", role = PreviewData.roles[3], startMinute = 16 * 60, endMinute = 16 * 60 + 45),
            initialMinutes = 45,
            running = null,
            onStart = {},
            onOpenRunning = {},
            onCancel = {},
        ) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun FocusStartContentBusyPreview() {
    KairoTheme {
        SheetFrame { FocusStartContent(
            target = FocusTarget(taskId = null, title = "DBMS lecture", role = PreviewData.roles[0], startMinute = 540, endMinute = 600),
            initialMinutes = 50,
            running = PreviewFocus.session,
            onStart = {},
            onOpenRunning = {},
            onCancel = {},
        ) }
    }
}
