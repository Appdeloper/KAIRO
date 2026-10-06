package com.kairo.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.EmptyState
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoScaffold
import com.kairo.app.ui.design.components.KairoSnackbarHost
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.glass
import com.kairo.app.ui.design.laneStyle
import com.kairo.app.ui.design.rememberKairoHaptics
import com.kairo.app.util.formatMinuteOfDay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = viewModel(
        factory = containerFactory { TasksViewModel(it.taskRepository, it.roleRepository, it.dateProvider, it.userPrefsRepository.prefs.map { p -> p.hiddenRoleIds }) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasksContent(
        state = state,
        onAdd = viewModel::add,
        onPutOnToday = viewModel::putOnToday,
        onToggleDone = viewModel::toggleDone,
        onDrop = viewModel::drop,
        onUndoDrop = viewModel::undoDrop,
    )
}

/** Plan > Tasks: what's on today, what's coming, what's not placed yet, and what's done. */
@Composable
fun TasksContent(
    state: TasksUiState,
    onAdd: (NewTaskInput) -> Unit,
    onPutOnToday: (Task, Int) -> Unit,
    onToggleDone: (Task) -> Unit,
    modifier: Modifier = Modifier,
    onDrop: (Task) -> Unit = {},
    onUndoDrop: (Task) -> Unit = {},
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf<Task?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val haptics = rememberKairoHaptics()
    val rolesById = state.roles.associateBy { it.id }

    val drop: (Task) -> Unit = { task ->
        onDrop(task)
        scope.launch {
            val result = snackbar.showSnackbar(resources.getString(R.string.tasks_dropped, task.title), resources.getString(R.string.msg_undo), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndoDrop(task)
        }
    }
    val done: (Task) -> Unit = { task ->
        if (task.status != TaskStatus.DONE) haptics.success()
        onToggleDone(task)
    }

    KairoScaffold(modifier = modifier, snackbarHost = { KairoSnackbarHost(snackbar) }, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingOrb() }
            return@KairoScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.bottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "add") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_hint_swipe), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.textTertiary, modifier = Modifier.weight(1f))
                    KairoTextButton(stringResource(R.string.tasks_add), { showAddSheet = true })
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.tasks_empty_title),
                        body = stringResource(R.string.tasks_empty_body),
                        actionLabel = stringResource(R.string.tasks_add),
                        onAction = { showAddSheet = true },
                    )
                }
            }
            group("today", R.string.tasks_group_today, state.today, rolesById, done, drop, onToday = null)
            group("earlier", R.string.tasks_group_earlier, state.carriedOver, rolesById, done, drop, onToday = { placing = it })
            group("upcoming", R.string.tasks_group_upcoming, state.upcoming, rolesById, done, drop, onToday = { placing = it })
            group("unscheduled", R.string.tasks_group_unscheduled, state.backlog, rolesById, done, drop, onToday = { placing = it })
            group("done", R.string.tasks_group_done, state.done, rolesById, done, drop = null, onToday = null)
        }
    }

    if (showAddSheet) {
        AddTaskSheet(
            roles = state.roles,
            hiddenRoleIds = state.hiddenRoleIds,
            onDismiss = { showAddSheet = false },
            onSave = { onAdd(it); showAddSheet = false },
        )
    }

    placing?.let { task ->
        // Suggest the next half hour: the most common "I'll do it soon" answer.
        val now = LocalTime.now()
        val suggested = ((now.hour * 60 + now.minute) / 30 + 1) * 30 % (24 * 60)
        TimePickerDialog(
            initialMinute = task.scheduledStartMinute ?: suggested,
            onDismiss = { placing = null },
            onConfirm = { minute -> onPutOnToday(task, minute); placing = null },
        )
    }
}

private fun LazyListScope.group(
    key: String,
    title: Int,
    tasks: List<Task>,
    roles: Map<Long, Role>,
    onDone: (Task) -> Unit,
    drop: ((Task) -> Unit)?,
    onToday: ((Task) -> Unit)?,
) {
    if (tasks.isEmpty()) return
    item(key = "h_$key") { SectionHeader(stringResource(title)) }
    items(tasks, key = { "${key}_${it.id}" }) { task ->
        SwipeableTask(task, roles[task.roleId], onDone, drop, onToday)
    }
}

/** Swipe right = done, swipe left = drop. TalkBack gets the same two actions as custom actions. */
@Composable
private fun SwipeableTask(task: Task, role: Role?, onDone: (Task) -> Unit, onDrop: ((Task) -> Unit)?, onToday: ((Task) -> Unit)?) {
    val colors = KairoTheme.colors
    val doneLabel = stringResource(if (task.status == TaskStatus.DONE) R.string.cd_mark_not_done else R.string.cd_mark_done)
    val dropLabel = stringResource(R.string.tasks_drop)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = onDrop != null,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onDone(task)
                SwipeToDismissBoxValue.EndToStart -> onDrop?.invoke(task)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // The row stays (it moves to another group or gets an Undo), so spring the swipe back.
            scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val accent = if (toEnd) colors.success else colors.error
            Row(
                Modifier.fillMaxSize().clip(Radius.medium).background(colors.tint(accent, 0.25f)).padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (toEnd) Icons.Filled.CheckCircle else Icons.Outlined.DeleteOutline, contentDescription = null, tint = accent)
                Spacer(Modifier.width(Spacing.sm))
                Text(if (toEnd) doneLabel else dropLabel, style = KairoTheme.type.labelLarge, color = accent)
            }
        },
        modifier = Modifier.semantics {
            customActions = listOfNotNull(
                CustomAccessibilityAction(doneLabel) { onDone(task); true },
                onDrop?.let { CustomAccessibilityAction(dropLabel) { it(task); true } },
            )
        },
    ) {
        TaskRow(task, role, onDone, onToday)
    }
}

@Composable
private fun TaskRow(task: Task, role: Role?, onDone: (Task) -> Unit, onToday: ((Task) -> Unit)?) {
    val colors = KairoTheme.colors
    val lane = laneStyle(role)
    val isDone = task.status == TaskStatus.DONE
    Row(
        Modifier.fillMaxWidth().glass(colors.surface1, Radius.medium).padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KairoIconButton(
            if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            stringResource(if (isDone) R.string.cd_mark_not_done else R.string.cd_mark_done),
            { onDone(task) },
            tint = if (isDone) colors.success else lane.color,
        )
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                task.title,
                style = KairoTheme.type.titleMedium,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                color = if (isDone) colors.textSecondary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(Radius.full).background(lane.color))
                Spacer(Modifier.width(Spacing.sm))
                Text(task.metaLine(role), style = KairoTheme.type.labelMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            task.nextStep?.let { Text(stringResource(R.string.timeline_next_step, it), style = KairoTheme.type.bodySmall, color = lane.color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onToday != null) {
            KairoIconButton(Icons.Outlined.Today, stringResource(R.string.tasks_put_on_today_cd, task.title), { onToday(task) }, tint = colors.primary)
        }
    }
}

@Composable
private fun Task.metaLine(role: Role?): String {
    val context = LocalContext.current
    val parts = listOfNotNull(
        role?.name,
        stringResource(R.string.task_duration_minutes, durationMinutes),
        stringResource(R.string.task_priority_short, priority),
        scheduledEpochDay?.takeIf { it != LocalDate.now().toEpochDay() }?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) },
        scheduledStartMinute?.let { formatMinuteOfDay(context, it) },
        deadlineEpochDay?.let { stringResource(R.string.task_due, LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
    )
    return parts.joinToString(stringResource(R.string.list_separator))
}

private val previewState = PreviewData.tasksState.copy(
    today = PreviewData.todayState.entries.mapNotNull { (it as? com.kairo.app.domain.TimelineEntry.TaskEntry)?.task }.filter { it.status != TaskStatus.DONE },
    done = PreviewData.todayState.entries.mapNotNull { (it as? com.kairo.app.domain.TimelineEntry.TaskEntry)?.task }.filter { it.status == TaskStatus.DONE },
    loaded = true,
)

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 900)
@Composable
private fun TasksContentPreview() {
    KairoTheme { TasksContent(state = previewState, onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun TasksContentEmptyPreview() {
    KairoTheme { TasksContent(state = TasksUiState(roles = PreviewData.roles, loaded = true), onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {}) }
}

/** Shared with screenshot tests. */
object TasksPreviewState { val sample get() = previewState }
