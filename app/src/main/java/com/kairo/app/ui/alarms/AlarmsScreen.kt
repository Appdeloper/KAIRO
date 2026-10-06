package com.kairo.app.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.AlarmPlan
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.DayPicker
import com.kairo.app.ui.design.components.EmptyState
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.Tone
import com.kairo.app.ui.design.components.TopBar
import com.kairo.app.util.formatMinuteOfDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

fun alarmsViewModelFactory() = containerFactory { AlarmsViewModel(it.alarmRepository, it.timetableRepository, it.userPrefsRepository) }

@Composable
fun AlarmsScreen(viewModel: AlarmsViewModel = viewModel(factory = alarmsViewModelFactory())) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val health = rememberAlarmHealth(state.next?.second)
    val fix = rememberHealthFixer()
    val defaultLabel = stringResource(R.string.alarm_wake_label)
    AlarmsContent(
        state = state,
        health = health,
        onFix = fix,
        onSave = { alarm ->
            viewModel.save(alarm)
            // Saving an alarm is when the ring screen starts to matter: ask for notifications now.
            if (alarm.enabled && HealthIssue.NOTIFICATIONS in health.missingPermissions) fix(HealthIssue.NOTIFICATIONS)
        },
        onDelete = viewModel::delete,
        onToggleEnabled = viewModel::setEnabled,
        onToggleDay = viewModel::toggleDay,
        onToggleSkip = viewModel::toggleSkipNext,
        onCreateWakeAlarm = {
            viewModel.createWeekdayWakeAlarm(state.wakeMinute, defaultLabel)
            // Creating an alarm is the moment to ask for notifications (needed for the ring screen).
            if (health.missingPermissions.contains(HealthIssue.NOTIFICATIONS)) fix(HealthIssue.NOTIFICATIONS)
        },
    )
}

@Composable
fun AlarmsContent(
    state: AlarmsUiState,
    health: AlarmHealth,
    onFix: (HealthIssue) -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onToggleEnabled: (Alarm, Boolean) -> Unit,
    onToggleDay: (Alarm, DayOfWeek) -> Unit,
    onToggleSkip: (AlarmRowUi) -> Unit,
    onCreateWakeAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    healthExpanded: Boolean = false,
) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    val context = LocalContext.current
    val newAlarm = { editing = Alarm(hour = state.wakeMinute / 60, minute = state.wakeMinute % 60, daysOfWeekMask = AlarmDays.WEEKDAYS) }
    Column(modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.nav_alarms)) {
            KairoTextButton(stringResource(R.string.alarm_add), newAlarm)
        }
        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingOrb() }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.bottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "next") { NextAlarmSummary(state.next) }
            // No separate banner here: the health section is the one place for these, and it opens by
            // itself when exact alarms are blocked, the one issue that stops every alarm.
            item(key = "health") { AlarmHealthSection(health, onFix, initiallyExpanded = healthExpanded || !health.exactAlarms) }
            if (state.rows.isEmpty()) {
                item(key = "proposal") { WakeAlarmProposal(formatMinuteOfDay(context, state.wakeMinute), onCreateWakeAlarm) }
            } else {
                item(key = "header") { SectionHeader(stringResource(R.string.alarm_list_header)) }
            }
            items(state.rows, key = { it.alarm.id }) { row ->
                AlarmRow(row, onClick = { editing = row.alarm }, onToggleEnabled, onToggleDay, onToggleSkip)
            }
        }
    }
    editing?.let { alarm ->
        AlarmEditorSheet(
            initial = alarm,
            blocks = state.blocks,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(it); editing = null },
        )
    }
}

/** The one thing most people open this tab for: when does the next alarm ring? */
@Composable
private fun NextAlarmSummary(next: Pair<AlarmPlan, Instant>?) {
    val colors = KairoTheme.colors
    GlassCard(level = GlassLevel.TWO, elevation = Elevation.GLOW) {
        Text(stringResource(R.string.alarm_next_title).uppercase(), style = KairoTheme.type.labelSmall, color = colors.textSecondary)
        if (next == null) {
            Text(stringResource(R.string.alarm_none_set), style = KairoTheme.type.titleLarge)
            Text(stringResource(R.string.alarm_none_set_body), style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
        } else {
            val (plan, at) = next
            val local = at.atZone(ZoneId.systemDefault())
            Text(local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = KairoTheme.numbers.large)
            val minutes = ((at.toEpochMilli() - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(0)
            Text(
                stringResource(
                    R.string.alarm_next_detail,
                    local.dayOfWeek.getDisplayName(TextStyle.FULL, com.kairo.app.ui.components.currentLocale()),
                    com.kairo.app.ui.today.durationText(minutes),
                    plan.label.ifBlank { stringResource(R.string.alarm_default_label) },
                ),
                style = KairoTheme.type.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/** Shown instead of auto-creating anything: the user must confirm the suggested wake-up alarm. */
@Composable
private fun WakeAlarmProposal(wakeTime: String, onCreate: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.alarm_proposal_title),
        body = stringResource(R.string.alarm_proposal_text, wakeTime),
        actionLabel = stringResource(R.string.alarm_proposal_confirm, wakeTime),
        onAction = onCreate,
    )
}

@Composable
private fun AlarmRow(
    row: AlarmRowUi,
    onClick: () -> Unit,
    onToggleEnabled: (Alarm, Boolean) -> Unit,
    onToggleDay: (Alarm, DayOfWeek) -> Unit,
    onToggleSkip: (AlarmRowUi) -> Unit,
) {
    val context = LocalContext.current
    val colors = KairoTheme.colors
    val alarm = row.alarm
    val time = formatMinuteOfDay(context, row.plan.minuteOfDay)
    GlassCard(onClick = onClick, onClickLabel = stringResource(R.string.alarm_edit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, style = KairoTheme.numbers.large, color = if (alarm.enabled) colors.textPrimary else colors.textTertiary)
                Text(alarmSubtitle(row), style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { onToggleEnabled(alarm, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.surface3, uncheckedBorderColor = colors.outlineStrong,
                ),
                modifier = Modifier.semantics { contentDescription = time },
            )
        }
        if (alarm.type != AlarmType.BLOCK && alarm.type != AlarmType.ONE_SHOT) {
            DayChips(alarm.daysOfWeekMask, onToggle = { onToggleDay(alarm, it) }, modifier = if (alarm.enabled) Modifier else Modifier.alpha(0.5f))
        }
        if (alarm.enabled && alarm.daysOfWeekMask != AlarmDays.ONE_SHOT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alarm.skipNextOnce) StatusPill(stringResource(R.string.alarm_skipping_next), tone = Tone.WARNING)
                Spacer(Modifier.weight(1f))
                KairoTextButton(stringResource(if (alarm.skipNextOnce) R.string.alarm_unskip else R.string.alarm_skip_next), { onToggleSkip(row) })
            }
        }
    }
}

@Composable
private fun alarmSubtitle(row: AlarmRowUi): String {
    val label = row.alarm.label.ifBlank { stringResource(R.string.alarm_default_label) }
    val next = when {
        !row.alarm.enabled -> stringResource(R.string.alarm_off)
        row.nextRing != null -> stringResource(R.string.alarm_next_ring, formatInstant(row.nextRing))
        else -> stringResource(R.string.alarm_on)
    }
    return stringResource(R.string.alarm_row_subtitle, label, next)
}

/** M T W T F S S, Monday first to match the timetable. Each day is a full touch target. */
@Composable
fun DayChips(mask: Int, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    DayPicker(
        selected = DayOfWeek.entries.filter { AlarmDays.contains(mask, it) }.map { it.value }.toSet(),
        onToggle = { onToggle(DayOfWeek.of(it)) },
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 900)
@Composable
private fun AlarmsContentPreview() {
    val wake = Alarm(id = 1, label = "Wake up", hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS)
    val gym = Alarm(id = 2, label = "Gym", hour = 18, minute = 0, daysOfWeekMask = AlarmDays.EVERY_DAY, enabled = false)
    KairoTheme {
        AlarmsContent(
            state = AlarmsUiState(
                rows = listOf(wake, gym).map { AlarmRowUi(it, AlarmPlans.resolve(it, null), null) },
                blocks = PreviewData.blocks,
                loaded = true,
            ),
            health = AlarmHealth(exactAlarms = false, fullScreen = true, notifications = true, batteryUnrestricted = true, alarmVolumeAudible = true, nextAlarm = null),
            onFix = {}, onSave = {}, onDelete = {}, onToggleEnabled = { _, _ -> }, onToggleDay = { _, _ -> }, onToggleSkip = {}, onCreateWakeAlarm = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 800)
@Composable
private fun AlarmsEmptyPreview() {
    KairoTheme {
        AlarmsContent(
            state = AlarmsUiState(loaded = true), health = AlarmHealth.ALL_GOOD,
            onFix = {}, onSave = {}, onDelete = {}, onToggleEnabled = { _, _ -> }, onToggleDay = { _, _ -> }, onToggleSkip = {}, onCreateWakeAlarm = {},
        )
    }
}
