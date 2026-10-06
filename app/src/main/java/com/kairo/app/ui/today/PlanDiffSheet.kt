package com.kairo.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.plan.Agenda
import com.kairo.app.domain.plan.Change
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.domain.plan.Reason
import com.kairo.app.domain.plan.RemovedItem
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Rule 3: every AI- or command-made change is previewed here before anything is written. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDiffSheet(diff: PlanDiff, today: LocalDate, onApply: () -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PlanDiffContent(diff, today, onApply, onCancel)
    }
}

@Composable
fun PlanDiffContent(diff: PlanDiff, today: LocalDate, onApply: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val agenda = diff.agenda
        Text(
            if (agenda != null) dateLabel(agenda.date, today).replaceFirstChar { it.uppercase() } else stringResource(R.string.plan_title_preview),
            style = MaterialTheme.typography.titleLarge,
        )
        agenda?.let { AgendaList(it) }
        diff.changes.forEach { ChangeRow(it, today) }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (diff.canApply) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApply) { Text(stringResource(R.string.plan_apply)) }
            } else {
                Button(onClick = onCancel) { Text(stringResource(R.string.plan_close)) }
            }
        }
    }
}

@Composable
private fun AgendaList(agenda: Agenda) {
    val context = LocalContext.current
    if (agenda.entries.isEmpty()) {
        Text(
            stringResource(if (agenda.nextOnly) R.string.agenda_next_empty else R.string.agenda_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    agenda.entries.forEach { entry ->
        val start = entry.startMinute
        val end = entry.endMinute
        val time = if (start != null && end != null) {
            stringResource(R.string.time_range, formatMinuteOfDay(context, start), formatMinuteOfDay(context, end))
        } else {
            stringResource(R.string.today_anytime)
        }
        val icon = if (entry is TimelineEntry.Block) Icons.Outlined.Block else Icons.Outlined.Schedule
        LineItem(icon, MaterialTheme.colorScheme.primary, stringResource(R.string.agenda_item, time, entry.title))
    }
}

@Composable
private fun ChangeRow(change: Change, today: LocalDate) {
    when (change) {
        is Change.Added -> LineItem(Icons.Outlined.AddCircleOutline, KairoTheme.colors.success, stringResource(R.string.change_added, change.task.title, slotLabel(change.task, today)))
        is Change.Moved -> {
            val text = if (change.before.scheduledStartMinute == null || change.before.scheduledEpochDay == null) {
                stringResource(R.string.change_scheduled, change.after.title, slotLabel(change.after, today))
            } else {
                stringResource(R.string.change_moved, change.after.title, slotLabel(change.before, today), slotLabel(change.after, today))
            }
            LineItem(Icons.Outlined.Schedule, KairoTheme.colors.primary, text)
        }
        is Change.Removed -> when (val item = change.item) {
            is RemovedItem.TaskUnscheduled -> LineItem(
                Icons.Outlined.RemoveCircleOutline, KairoTheme.colors.textTertiary,
                stringResource(R.string.change_unscheduled, item.before.title, item.before.scheduledEpochDay?.let { dateLabel(LocalDate.ofEpochDay(it), today) }.orEmpty()),
            )
            is RemovedItem.TaskCompleted -> LineItem(Icons.Outlined.CheckCircle, KairoTheme.colors.success, stringResource(R.string.change_completed, item.before.title))
            is RemovedItem.BlockSkipped -> LineItem(
                Icons.Outlined.RemoveCircleOutline, KairoTheme.colors.textTertiary,
                stringResource(R.string.change_block_skipped, item.block.title, dateLabel(item.date, today)),
            )
        }
        is Change.Conflict -> LineItem(Icons.Outlined.Block, MaterialTheme.colorScheme.error, reasonText(change.reason, today))
        is Change.Warning -> LineItem(Icons.Outlined.WarningAmber, KairoTheme.colors.warning, reasonText(change.reason, today))
    }
}

@Composable
private fun LineItem(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun reasonText(reason: Reason, today: LocalDate): String {
    val context = LocalContext.current
    fun time(m: Int) = formatMinuteOfDay(context, m)
    return when (reason) {
        is Reason.NotFound -> stringResource(R.string.reason_not_found, reason.query)
        is Reason.Ambiguous -> stringResource(R.string.reason_ambiguous, reason.query, reason.candidates.joinToString(stringResource(R.string.list_separator)))
        is Reason.FixedBlockImmovable -> stringResource(R.string.reason_fixed, reason.blockTitle)
        is Reason.NoFreeSlot -> stringResource(R.string.reason_no_slot, reason.taskTitle, dateLabel(reason.date, today))
        is Reason.NoSlotBeforeDeadline -> stringResource(R.string.reason_no_slot_deadline, reason.taskTitle, dateLabel(reason.deadline, today))
        is Reason.DeadlinePassed -> stringResource(R.string.reason_deadline_passed, reason.taskTitle, dateLabel(reason.deadline, today))
        is Reason.DateInPast -> stringResource(R.string.reason_date_past, dateLabel(reason.date, today))
        is Reason.TimeInPast -> stringResource(R.string.reason_time_past, time(reason.minute))
        is Reason.OverBudget -> stringResource(R.string.reason_over_budget, reason.roleName, reason.plannedMinutes, reason.budgetMinutes)
        is Reason.ShiftedAroundFixed -> reason.obstacleTitle
            ?.let { stringResource(R.string.reason_shifted_block, reason.taskTitle, time(reason.requestedMinute), time(reason.placedMinute), it) }
            ?: stringResource(R.string.reason_shifted_alarm, reason.taskTitle, time(reason.requestedMinute), time(reason.placedMinute))
        is Reason.OverlapsTask -> stringResource(R.string.reason_overlaps, reason.taskTitle, reason.otherTitle)
        is Reason.OutsideDayHours -> stringResource(R.string.reason_outside_hours, reason.taskTitle)
        is Reason.PlacedOnLaterDay -> stringResource(R.string.reason_later_day, reason.taskTitle, dateLabel(reason.date, today))
        is Reason.AlreadyDone -> stringResource(R.string.reason_already_done, reason.taskTitle)
        is Reason.AlreadySkipped -> stringResource(R.string.reason_already_skipped, reason.blockTitle)
        is Reason.NothingToRollOver -> stringResource(R.string.reason_nothing_rollover, dateLabel(reason.date, today))
        Reason.NoRoles -> stringResource(R.string.reason_no_roles)
    }
}

@Composable
private fun slotLabel(task: Task, today: LocalDate): String {
    val context = LocalContext.current
    val day = task.scheduledEpochDay?.let { dateLabel(LocalDate.ofEpochDay(it), today) }.orEmpty()
    val start = task.scheduledStartMinute ?: return day
    val range = stringResource(R.string.time_range, formatMinuteOfDay(context, start), formatMinuteOfDay(context, start + task.durationMinutes))
    return stringResource(R.string.day_and_time, day, range)
}

@Composable
private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun PlanDiffContentPreview() {
    KairoTheme { PlanDiffContent(diff = PreviewData.sampleDiff, today = PreviewData.today, onApply = {}, onCancel = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun PlanDiffAgendaPreview() {
    KairoTheme { PlanDiffContent(diff = PreviewData.sampleAgenda, today = PreviewData.today, onApply = {}, onCancel = {}) }
}
