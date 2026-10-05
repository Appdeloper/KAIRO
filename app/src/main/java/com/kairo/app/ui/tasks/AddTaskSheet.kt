package com.kairo.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.Role
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RolePicker
import com.kairo.app.ui.theme.KairoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DurationOptions = listOf(15, 30, 45, 60, 90, 120)
private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(roles: List<Role>, onDismiss: () -> Unit, onSave: (NewTaskInput) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AddTaskForm(roles, onSave)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskForm(roles: List<Role>, onSave: (NewTaskInput) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var roleId by rememberSaveable { mutableStateOf(roles.firstOrNull()?.id) }
    var duration by rememberSaveable { mutableIntStateOf(30) }
    var priority by rememberSaveable { mutableIntStateOf(3) }
    var deadline by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tasks_add), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.task_title_label)) },
            isError = showErrors && title.isBlank(),
            supportingText = if (showErrors && title.isBlank()) {
                { Text(stringResource(R.string.task_error_title)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        RolePicker(roles = roles, selectedId = roleId, onSelect = { roleId = it })

        Text(stringResource(R.string.task_duration_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DurationOptions.forEach { minutes ->
                FilterChip(
                    selected = duration == minutes,
                    onClick = { duration = minutes },
                    label = { Text(stringResource(R.string.task_duration_minutes, minutes)) },
                )
            }
        }

        Text(stringResource(R.string.task_priority_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..4).forEach { p ->
                SegmentedButton(
                    selected = priority == p,
                    onClick = { priority = p },
                    shape = SegmentedButtonDefaults.itemShape(index = p - 1, count = 4),
                ) { Text(stringResource(R.string.task_priority_short, p)) }
            }
        }

        Text(stringResource(R.string.task_deadline_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingDate = true }) {
                Text(
                    deadline?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                        ?: stringResource(R.string.task_deadline_none),
                )
            }
            if (deadline != null) {
                TextButton(onClick = { deadline = null }) { Text(stringResource(R.string.task_deadline_clear)) }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val role = roleId
                if (title.isBlank() || role == null) {
                    showErrors = true
                } else {
                    onSave(NewTaskInput(title, role, duration, priority, deadline))
                }
            }) { Text(stringResource(R.string.action_save)) }
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }

    if (pickingDate) {
        // DatePicker works in UTC millis at midnight, so integer division gives the exact epoch day.
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = deadline?.times(MILLIS_PER_DAY))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { deadline = it / MILLIS_PER_DAY }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun AddTaskFormPreview() {
    KairoTheme { AddTaskForm(roles = PreviewData.roles, onSave = {}) }
}
