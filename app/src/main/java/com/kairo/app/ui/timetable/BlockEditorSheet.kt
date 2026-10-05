package com.kairo.app.ui.timetable

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.domain.BlockError
import com.kairo.app.domain.BlockValidation
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RolePicker
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.components.currentLocale
import com.kairo.app.ui.theme.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import java.time.DayOfWeek
import java.time.format.TextStyle

private enum class TimeField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditorSheet(
    initial: FixedBlock,
    roles: List<Role>,
    onDismiss: () -> Unit,
    onSave: (FixedBlock) -> Unit,
    onDelete: (FixedBlock) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BlockEditorForm(initial, roles, onSave, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorForm(
    initial: FixedBlock,
    roles: List<Role>,
    onSave: (FixedBlock) -> Unit,
    onDelete: (FixedBlock) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var pickingTime by remember { mutableStateOf<TimeField?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isNew = initial.id == 0L
    val error = BlockValidation.validate(draft.title, draft.dayOfWeek, draft.startMinute, draft.endMinute)
    val roleMissing = roles.none { it.id == draft.roleId }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(if (isNew) R.string.timetable_new_block else R.string.timetable_edit_block),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.block_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.location.orEmpty(),
            onValueChange = { draft = draft.copy(location = it) },
            label = { Text(stringResource(R.string.block_location_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = draft.dayOfWeek == day.value,
                    onClick = { draft = draft.copy(dayOfWeek = day.value) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, currentLocale())) },
                )
            }
        }
        RolePicker(roles = roles, selectedId = draft.roleId, onSelect = { draft = draft.copy(roleId = it) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickingTime = TimeField.START }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.block_start, formatMinuteOfDay(context, draft.startMinute)))
            }
            OutlinedButton(onClick = { pickingTime = TimeField.END }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.block_end, formatMinuteOfDay(context, draft.endMinute)))
            }
        }
        if (showErrors && (error != null || roleMissing)) {
            Text(
                stringResource(
                    when (error) {
                        BlockError.EMPTY_TITLE -> R.string.block_error_title
                        BlockError.END_NOT_AFTER_START -> R.string.block_error_time
                        BlockError.BAD_DAY -> R.string.block_error_day
                        null -> R.string.block_error_role
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            if (!isNew) {
                TextButton(onClick = { onDelete(initial) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (error == null && !roleMissing) onSave(draft) else showErrors = true
            }) { Text(stringResource(R.string.action_save)) }
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }

    pickingTime?.let { field ->
        TimePickerDialog(
            initialMinute = if (field == TimeField.START) draft.startMinute else draft.endMinute,
            onDismiss = { pickingTime = null },
            onConfirm = { minute ->
                draft = if (field == TimeField.START) {
                    // Keep the block's length when the start moves, which is what people expect when shifting a class.
                    val length = draft.endMinute - draft.startMinute
                    draft.copy(startMinute = minute, endMinute = (minute + length.coerceAtLeast(15)).coerceAtMost(23 * 60 + 59))
                } else {
                    draft.copy(endMinute = minute)
                }
                pickingTime = null
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16)
@Composable
private fun BlockEditorFormPreview() {
    KairoTheme {
        BlockEditorForm(initial = PreviewData.blocks.first(), roles = PreviewData.roles, onSave = {}, onDelete = {})
    }
}
