package com.kairo.app.ui.today

import com.kairo.app.util.beta.BetaEvent
import com.kairo.app.util.beta.Events
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
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
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.SheetFrame
import com.kairo.app.ui.design.components.TimelineItem
import com.kairo.app.ui.design.laneStyle
import com.kairo.app.ui.design.rememberKairoHaptics
import com.kairo.app.util.formatMinuteOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Rule 3: every AI- or command-made change is previewed here before anything is written. */
@Composable
fun PlanDiffSheet(diff: PlanDiff, today: LocalDate, onApply: () -> Unit, onCancel: () -> Unit) {
    val cancel = {
        Events.record(BetaEvent.PLAN_CANCELLED)
        onCancel()
    }
    KairoBottomSheet(onDismissRequest = cancel) {
        PlanDiffContent(diff, today, onApply, cancel)
    }
}

/** How a change reads at a glance: icon, colour and a word, so colour is never the only signal. */
private enum class ChangeKind { ADDED, MOVED, REMOVED, DONE, CONFLICT, NOTE }

private data class ChangeRowUi(val kind: ChangeKind, val title: String, val before: String?, val after: String?, val reason: String?)

@Composable
fun PlanDiffContent(diff: PlanDiff, today: LocalDate, onApply: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberKairoHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val agenda = diff.agenda
        Text(
            if (agenda != null) dateLabel(agenda.date, today).replaceFirstChar { it.uppercase() } else stringResource(R.string.plan_title_preview),
            style = KairoTheme.type.headlineSmall,
        )
        if (agenda == null) {
            val count = diff.changes.count { it !is Change.Warning && it !is Change.Conflict }
            Text(
                pluralStringResource(R.plurals.plan_change_count, count, count),
                style = KairoTheme.type.bodyMedium,
                color = KairoTheme.colors.textSecondary,
            )
        }
        agenda?.let { AgendaList(it) }
        diff.changes.forEach { ChangeRow(changeRow(it, today)) }

        Spacer(Modifier.height(Spacing.sm))
        if (diff.canApply) {
            PrimaryButton(
                stringResource(R.string.plan_apply),
                onClick = {
                    haptics.tick()
                    onApply()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            KairoTextButton(stringResource(R.string.action_cancel), onCancel, modifier = Modifier.fillMaxWidth(), color = KairoTheme.colors.textSecondary)
        } else {
            SecondaryButton(stringResource(R.string.plan_close), onCancel, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AgendaList(agenda: Agenda) {
    val context = LocalContext.current
    if (agenda.entries.isEmpty()) {
        Text(
            stringResource(if (agenda.nextOnly) R.string.agenda_next_empty else R.string.agenda_empty),
            style = KairoTheme.type.bodyLarge,
            color = KairoTheme.colors.textSecondary,
        )
    }
    agenda.entries.forEach { entry ->
        val start = entry.startMinute
        val end = entry.endMinute
        TimelineItem(
            startTime = start?.let { formatMinuteOfDay(context, it) } ?: stringResource(R.string.today_anytime),
            endTime = end?.takeIf { start != null }?.let { formatMinuteOfDay(context, it) },
            title = entry.title,
            laneColor = laneStyle(entry.role).color,
            laneLabel = entry.role?.name,
            locked = entry is TimelineEntry.Block,
            lockedDescription = stringResource(R.string.cd_fixed_block),
        )
    }
}

@Composable
private fun changeRow(change: Change, today: LocalDate): ChangeRowUi = when (change) {
    is Change.Added -> ChangeRowUi(ChangeKind.ADDED, change.task.title, null, slotLabel(change.task, today), null)
    is Change.Moved -> {
        val unplaced = change.before.scheduledStartMinute == null || change.before.scheduledEpochDay == null
        ChangeRowUi(
            ChangeKind.MOVED,
            change.after.title,
            if (unplaced) stringResource(R.string.change_unplaced) else slotLabel(change.before, today),
            slotLabel(change.after, today),
            null,
        )
    }
    is Change.Removed -> when (val item = change.item) {
        is RemovedItem.TaskUnscheduled -> ChangeRowUi(
            ChangeKind.REMOVED, item.before.title, slotLabel(item.before, today), stringResource(R.string.change_back_to_list), null,
        )
        is RemovedItem.TaskCompleted -> ChangeRowUi(ChangeKind.DONE, item.before.title, null, stringResource(R.string.change_marked_done), null)
        is RemovedItem.BlockSkipped -> ChangeRowUi(
            ChangeKind.REMOVED, item.block.title, dateLabel(item.date, today), stringResource(R.string.change_skipped_once), null,
        )
    }
    is Change.Conflict -> ChangeRowUi(ChangeKind.CONFLICT, stringResource(R.string.change_label_conflict), null, null, reasonText(change.reason, today))
    is Change.Warning -> ChangeRowUi(ChangeKind.NOTE, stringResource(R.string.change_label_note), null, null, reasonText(change.reason, today))
}

@Composable
private fun ChangeRow(row: ChangeRowUi) {
    val colors = KairoTheme.colors
    val (accent, icon, label) = when (row.kind) {
        ChangeKind.ADDED -> Triple(colors.success, Icons.Outlined.Add, R.string.change_label_added)
        ChangeKind.MOVED -> Triple(colors.moment, Icons.Outlined.Schedule, R.string.change_label_moved)
        ChangeKind.REMOVED -> Triple(colors.textTertiary, Icons.Outlined.RemoveCircleOutline, R.string.change_label_removed)
        ChangeKind.DONE -> Triple(colors.success, Icons.Outlined.CheckCircle, R.string.change_label_done)
        ChangeKind.CONFLICT -> Triple(colors.error, Icons.Outlined.Block, R.string.change_label_conflict)
        ChangeKind.NOTE -> Triple(colors.warning, Icons.Outlined.WarningAmber, R.string.change_label_note)
    }
    GlassCard(level = GlassLevel.ONE, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(Radius.full).background(colors.tint(accent, 0.2f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(label).uppercase(), style = KairoTheme.type.labelSmall, color = accent)
                if (row.kind != ChangeKind.CONFLICT && row.kind != ChangeKind.NOTE) {
                    Text(
                        row.title,
                        style = KairoTheme.type.titleMedium,
                        textDecoration = if (row.kind == ChangeKind.REMOVED) TextDecoration.LineThrough else null,
                        color = if (row.kind == ChangeKind.REMOVED) colors.textSecondary else colors.textPrimary,
                    )
                }
                val line = when {
                    row.before != null && row.after != null -> stringResource(R.string.change_before_after, row.before, row.after)
                    row.after != null -> row.after
                    else -> null
                }
                if (line != null) Text(line, style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
                if (row.reason != null) Text(row.reason, style = KairoTheme.type.bodyMedium, color = colors.textPrimary)
            }
        }
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 760)
@Composable
private fun PlanDiffContentPreview() {
    KairoTheme { SheetFrame { PlanDiffContent(diff = PreviewData.sampleDiff, today = PreviewData.today, onApply = {}, onCancel = {}) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 760)
@Composable
private fun PlanDiffAgendaPreview() {
    KairoTheme { SheetFrame { PlanDiffContent(diff = PreviewData.sampleAgenda, today = PreviewData.today, onApply = {}, onCancel = {}) } }
}
