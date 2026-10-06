package com.kairo.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import com.kairo.app.ui.design.laneStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RoleDot
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.util.parseHexColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = viewModel(
        factory = containerFactory { TasksViewModel(it.taskRepository, it.roleRepository, it.dateProvider) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasksContent(
        state = state,
        onAdd = viewModel::add,
        onPutOnToday = viewModel::putOnToday,
        onToggleDone = viewModel::toggleDone,
    )
}

@Composable
fun TasksContent(
    state: TasksUiState,
    onAdd: (NewTaskInput) -> Unit,
    onPutOnToday: (Task, Int) -> Unit,
    onToggleDone: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf<Task?>(null) }
    val rolesById = state.roles.associateBy { it.id }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.backlog.isEmpty() && state.carriedOver.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tasks_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.carriedOver.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.tasks_carried_over)) }
                items(state.carriedOver, key = { "c${it.id}" }) { task ->
                    TaskCard(task, rolesById[task.roleId], onToday = { placing = task }, onToggleDone = { onToggleDone(task) })
                }
            }
            if (state.backlog.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.tasks_backlog)) }
                items(state.backlog, key = { "b${it.id}" }) { task ->
                    TaskCard(task, rolesById[task.roleId], onToday = { placing = task }, onToggleDone = { onToggleDone(task) })
                }
            }
        }
    }

    if (showAddSheet) {
        AddTaskSheet(
            roles = state.roles,
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TaskCard(task: Task, role: Role?, onToday: () -> Unit, onToggleDone: () -> Unit) {
    val roleColor = laneStyle(role).color
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleDone) {
                Icon(Icons.Outlined.Circle, contentDescription = stringResource(R.string.cd_task_not_done), tint = roleColor)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleDot(role, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(task.metaLine(role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AssistChip(
                onClick = onToday,
                label = { Text(stringResource(R.string.tasks_put_on_today)) },
                leadingIcon = { Icon(Icons.Outlined.Today, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun Task.metaLine(role: Role?): String {
    val parts = buildList {
        role?.let { add(it.name) }
        add(stringResource(R.string.task_duration_minutes, durationMinutes))
        add(stringResource(R.string.task_priority_short, priority))
        deadlineEpochDay?.let {
            add(stringResource(R.string.task_due, LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))))
        }
    }
    return parts.joinToString(stringResource(R.string.list_separator))
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TasksContentPreview() {
    KairoTheme {
        TasksContent(state = PreviewData.tasksState, onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TasksContentEmptyPreview() {
    KairoTheme {
        TasksContent(state = TasksUiState(roles = PreviewData.roles), onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {})
    }
}
