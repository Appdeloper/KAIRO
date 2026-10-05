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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    viewModel: TodayViewModel = viewModel(
        factory = containerFactory {
            TodayViewModel(it.taskRepository, it.timetableRepository, it.roleRepository, it.userPrefsRepository, it.dateProvider)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayContent(state = state, onTaskClick = viewModel::toggleDone)
}

@Composable
fun TodayContent(state: TodayUiState, onTaskClick: (Task) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state.firstName, state.dayPart, state.date) }
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
private fun Header(firstName: String, dayPart: DayPart, date: LocalDate) {
    val greetingRes = when (dayPart) {
        DayPart.MORNING -> R.string.greeting_morning
        DayPart.AFTERNOON -> R.string.greeting_afternoon
        DayPart.EVENING -> R.string.greeting_evening
        DayPart.NIGHT -> R.string.greeting_night
    }
    Column {
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
    val isDone = taskEntry?.isDone == true

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
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoleDot(entry.role, size = 8.dp)
                        Spacer(Modifier.width(6.dp))
                        val subtitle = listOfNotNull(entry.role?.name, (entry as? TimelineEntry.Block)?.block?.location)
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

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun TodayContentEmptyPreview() {
    KairoTheme {
        TodayContent(state = TodayUiState(firstName = "Aarav"), onTaskClick = {})
    }
}
