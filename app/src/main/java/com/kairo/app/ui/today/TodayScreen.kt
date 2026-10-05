package com.kairo.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.local.Task
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.alarms.AlarmPermissionBanner
import com.kairo.app.ui.alarms.formatInstant
import com.kairo.app.ui.alarms.rememberAlarmHealth
import com.kairo.app.ui.alarms.rememberHealthFixer
import com.kairo.app.ui.theme.KairoColors
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.AssistChip
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.ui.command.CommandFeedbackEffect
import com.kairo.app.ui.components.RoleDot
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.theme.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import com.kairo.app.util.parseHexColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onOpenAlarms: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(
        factory = containerFactory {
            TodayViewModel(
                it.taskRepository, it.timetableRepository, it.roleRepository, it.userPrefsRepository,
                it.dateProvider, it.planRepository, it.commandExecutor, it.commandParser, it.alarmRepository,
            )
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingDiff by viewModel.pendingDiff.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val alarmHealth = rememberAlarmHealth(alarms.next?.second)
    val fixAlarmIssue = rememberHealthFixer()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    CommandFeedbackEffect(viewModel.events, snackbarHostState, onUndo = viewModel::undo)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CommandBar(onSubmit = viewModel::submitCommand) },
    ) { padding ->
        TodayContent(
            state = state,
            onTaskClick = viewModel::toggleDone,
            onBriefMe = { context.startActivity(BriefingActivity.intent(context)) },
            alarmSlot = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Only nag about alarm permissions once the user actually relies on alarms.
                    if (alarms.anyEnabled) AlarmPermissionBanner(alarmHealth, fixAlarmIssue)
                    alarms.next?.let { (plan, at) -> NextAlarmChip(plan.label, at, onOpenAlarms) }
                }
            },
            modifier = Modifier.padding(padding),
        )
    }

    pendingDiff?.let { diff ->
        PlanDiffSheet(diff = diff, today = state.date, onApply = viewModel::applyPending, onCancel = viewModel::dismissPending)
    }
}

/** Temporary typed input for testing the parser and planner until voice arrives. */
@Composable
fun CommandBar(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.command_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = send, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.command_send))
        }
    }
}

@Composable
fun TodayContent(
    state: TodayUiState,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
    onBriefMe: () -> Unit = {},
    alarmSlot: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state.firstName, state.dayPart, state.date, onBriefMe) }
        item { alarmSlot() }
        item { ProgressCard(state) }
        if (state.entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(state.entries, key = { it.key() }) { entry ->
            TimelineRow(entry, onTaskClick)
        }
    }
}

private fun TimelineEntry.key(): String = when (this) {
    is TimelineEntry.Block -> "b${block.id}"
    is TimelineEntry.TaskEntry -> "t${task.id}"
}

@Composable
private fun Header(firstName: String, dayPart: DayPart, date: LocalDate, onBriefMe: () -> Unit) {
    val greetingRes = when (dayPart) {
        DayPart.MORNING -> R.string.greeting_morning
        DayPart.AFTERNOON -> R.string.greeting_afternoon
        DayPart.EVENING -> R.string.greeting_evening
        DayPart.NIGHT -> R.string.greeting_night
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(greetingRes, firstName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onBriefMe) { Text(stringResource(R.string.brief_me)) }
    }
}

@Composable
private fun ProgressCard(state: TodayUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress.fraction },
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    stringResource(R.string.today_progress_count, state.progress.done, state.progress.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.today_progress_label), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, onTaskClick: (Task) -> Unit) {
    val context = LocalContext.current
    val roleColor = entry.role?.let { parseHexColor(it.colorHex) } ?: Color.Gray
    val start = entry.startMinute
    val end = entry.endMinute
    val timeText = if (start != null && end != null) {
        stringResource(R.string.time_range, formatMinuteOfDay(context, start), formatMinuteOfDay(context, end))
    } else {
        stringResource(R.string.today_anytime)
    }
    val taskEntry = entry as? TimelineEntry.TaskEntry
    val isSkipped = (entry as? TimelineEntry.Block)?.skipped == true
    val isDone = taskEntry?.isDone == true
    val struck = isDone || isSkipped

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (taskEntry != null) Modifier.clickable { onTaskClick(taskEntry.task) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Role color strip: the fastest way to read "whose time is this" at a glance.
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(roleColor, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)),
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(timeText, style = MaterialTheme.typography.labelMedium, color = roleColor)
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (struck) TextDecoration.LineThrough else null,
                        color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoleDot(entry.role, size = 8.dp)
                        Spacer(Modifier.width(6.dp))
                        val subtitle = listOfNotNull(
                            entry.role?.name,
                            (entry as? TimelineEntry.Block)?.block?.location,
                            if (isSkipped) stringResource(R.string.timeline_skipped) else null,
                        )
                        Text(
                            subtitle.joinToString(stringResource(R.string.list_separator)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    taskEntry == null -> Icon(
                        Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.cd_fixed_block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    isDone -> Icon(Icons.Filled.CheckCircle, stringResource(R.string.cd_task_done), tint = roleColor)
                    else -> Icon(Icons.Outlined.Circle, stringResource(R.string.cd_task_not_done), tint = roleColor)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TodayContentPreview() {
    KairoTheme {
        TodayContent(state = PreviewData.todayState, onTaskClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun CommandBarPreview() {
    KairoTheme { CommandBar(onSubmit = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TodayContentEmptyPreview() {
    KairoTheme {
        TodayContent(state = TodayUiState(firstName = "Aarav"), onTaskClick = {})
    }
}

@Composable
fun NextAlarmChip(label: String, at: java.time.Instant, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null, tint = KairoColors.NeonCyan) },
        label = {
            Text(stringResource(R.string.today_next_alarm, formatInstant(at), label.ifBlank { stringResource(R.string.alarm_default_label) }))
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun NextAlarmChipPreview() {
    KairoTheme { NextAlarmChip("Wake up", java.time.Instant.parse("2026-10-06T01:30:00Z"), {}) }
}
