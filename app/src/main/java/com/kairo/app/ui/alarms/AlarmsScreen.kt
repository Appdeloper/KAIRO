package com.kairo.app.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.theme.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import java.time.DayOfWeek
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
) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Alarm(hour = state.wakeMinute / 60, minute = state.wakeMinute % 60, daysOfWeekMask = AlarmDays.WEEKDAYS)
            }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.alarm_add)) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AlarmPermissionBanner(health, onFix) }
            if (state.loaded && state.rows.isEmpty()) {
                item { WakeAlarmProposal(formatMinuteOfDay(context, state.wakeMinute), onCreateWakeAlarm) }
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

/** Shown instead of auto-creating anything: the user must confirm the suggested wake-up alarm. */
@Composable
private fun WakeAlarmProposal(wakeTime: String, onCreate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.alarm_proposal_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.alarm_proposal_text, wakeTime), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate) { Text(stringResource(R.string.alarm_proposal_confirm, wakeTime)) }
        }
    }
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
    val alarm = row.alarm
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatMinuteOfDay(context, row.plan.minuteOfDay),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(alarmSubtitle(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = alarm.enabled, onCheckedChange = { onToggleEnabled(alarm, it) })
            }
            if (alarm.type != AlarmType.BLOCK && alarm.type != AlarmType.ONE_SHOT) {
                DayChips(alarm.daysOfWeekMask, onToggle = { onToggleDay(alarm, it) })
            }
            if (alarm.enabled && alarm.daysOfWeekMask != AlarmDays.ONE_SHOT) {
                TextButton(onClick = { onToggleSkip(row) }) {
                    Text(stringResource(if (alarm.skipNextOnce) R.string.alarm_unskip else R.string.alarm_skip_next))
                }
            }
        }
    }
}

@Composable
private fun alarmSubtitle(row: AlarmRowUi): String {
    val label = row.alarm.label.ifBlank { stringResource(R.string.alarm_default_label) }
    val next = row.nextRing?.let { stringResource(R.string.alarm_next_ring, formatInstant(it)) } ?: stringResource(R.string.alarm_off)
    return stringResource(R.string.alarm_row_subtitle, label, next)
}

/** M T W T F S S, Monday first to match the timetable. */
@Composable
fun DayChips(mask: Int, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    val locale = com.kairo.app.ui.components.currentLocale()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = AlarmDays.contains(mask, day),
                onClick = { onToggle(day) },
                label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 640)
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

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AlarmsEmptyPreview() {
    KairoTheme {
        AlarmsContent(
            state = AlarmsUiState(loaded = true), health = AlarmHealth.ALL_GOOD,
            onFix = {}, onSave = {}, onDelete = {}, onToggleEnabled = { _, _ -> }, onToggleDay = { _, _ -> }, onToggleSkip = {}, onCreateWakeAlarm = {},
        )
    }
}
