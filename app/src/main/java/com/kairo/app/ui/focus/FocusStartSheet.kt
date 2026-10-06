package com.kairo.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.local.Role
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.focus.FocusDurations
import com.kairo.app.domain.focus.FocusRules
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RoleDot
import com.kairo.app.ui.design.KairoTheme

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

/** Same sheet style as the PlanDiff preview: full height, no half-expanded state. */
@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.focus_start_title), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleDot(target.role, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Text(target.title, style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusDurations.CHIPS.forEach { option ->
                FilterChip(
                    selected = !custom && minutes == option,
                    onClick = {
                        custom = false
                        minutes = option
                    },
                    label = { Text(stringResource(R.string.focus_minutes, option)) },
                )
            }
            FilterChip(
                selected = custom,
                onClick = { custom = true },
                label = { Text(if (custom) stringResource(R.string.focus_custom_value, minutes) else stringResource(R.string.focus_custom)) },
            )
        }
        if (custom) {
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = FocusDurations.snap(it) },
                valueRange = FocusRules.MIN_MINUTES.toFloat()..FocusRules.MAX_MINUTES.toFloat(),
            )
        }
        running?.let { RunningNotice(it, onOpenRunning) }
        if (notificationsOff) {
            Text(stringResource(R.string.focus_notifications_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onStart(minutes) }, enabled = running == null) {
                Text(stringResource(R.string.focus_start_button, minutes))
            }
        }
    }
}

/** One session at a time: say so plainly and offer the running one instead of silently refusing. */
@Composable
private fun RunningNotice(running: FocusSession, onOpenRunning: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.focus_already_running, running.blockLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenRunning) { Text(stringResource(R.string.focus_open_running)) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun FocusStartContentPreview() {
    KairoTheme {
        FocusStartContent(
            target = FocusTarget(taskId = 2, title = "Edit reel #12", role = PreviewData.roles[3], startMinute = 16 * 60, endMinute = 16 * 60 + 45),
            initialMinutes = 45,
            running = null,
            onStart = {},
            onOpenRunning = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun FocusStartContentBusyPreview() {
    KairoTheme {
        FocusStartContent(
            target = FocusTarget(taskId = null, title = "DBMS lecture", role = PreviewData.roles[0], startMinute = 540, endMinute = 600),
            initialMinutes = 50,
            running = PreviewFocus.session,
            onStart = {},
            onOpenRunning = {},
            onCancel = {},
        )
    }
}
