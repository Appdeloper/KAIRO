package com.kairo.app.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.domain.BlockError
import com.kairo.app.domain.BlockValidation
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.DayPicker
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.LanePicker
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.SheetFrame
import com.kairo.app.ui.design.components.TimeField as TimeButton
import com.kairo.app.util.formatMinuteOfDay

private enum class TimeField { START, END }

@Composable
fun BlockEditorSheet(
    initial: FixedBlock,
    roles: List<Role>,
    onDismiss: () -> Unit,
    onSave: (FixedBlock) -> Unit,
    onDelete: (FixedBlock) -> Unit,
    hiddenRoleIds: Set<Long> = emptySet(),
) {
    KairoBottomSheet(onDismissRequest = onDismiss) {
        BlockEditorForm(initial, roles, hiddenRoleIds, onSave, onDelete)
    }
}

@Composable
private fun BlockEditorForm(
    initial: FixedBlock,
    roles: List<Role>,
    hiddenRoleIds: Set<Long>,
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
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(if (isNew) R.string.timetable_new_block else R.string.timetable_edit_block), style = KairoTheme.type.headlineSmall)
        KairoTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            label = stringResource(R.string.block_title_label),
            error = if (showErrors && error == BlockError.EMPTY_TITLE) stringResource(R.string.block_error_title) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        KairoTextField(
            value = draft.location.orEmpty(),
            onValueChange = { draft = draft.copy(location = it) },
            label = stringResource(R.string.block_location_label),
        )
        SectionHeader(stringResource(R.string.block_day_label))
        DayPicker(selected = setOf(draft.dayOfWeek), onToggle = { draft = draft.copy(dayOfWeek = it) })
        SectionHeader(stringResource(R.string.block_lane_label))
        LanePicker(roles = roles, selectedId = draft.roleId, onSelect = { draft = draft.copy(roleId = it) }, hiddenIds = hiddenRoleIds)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TimeButton(stringResource(R.string.block_start_label), formatMinuteOfDay(context, draft.startMinute), { pickingTime = TimeField.START }, Modifier.weight(1f))
            TimeButton(stringResource(R.string.block_end_label), formatMinuteOfDay(context, draft.endMinute), { pickingTime = TimeField.END }, Modifier.weight(1f))
        }
        if (showErrors && (error == BlockError.END_NOT_AFTER_START || error == BlockError.BAD_DAY || roleMissing)) {
            Text(
                stringResource(
                    when (error) {
                        BlockError.END_NOT_AFTER_START -> R.string.block_error_time
                        BlockError.BAD_DAY -> R.string.block_error_day
                        else -> R.string.block_error_role
                    },
                ),
                style = KairoTheme.type.bodyMedium,
                color = KairoTheme.colors.error,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        PrimaryButton(
            stringResource(if (isNew) R.string.timetable_add else R.string.action_save),
            onClick = { if (error == null && !roleMissing) onSave(draft) else showErrors = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!isNew) {
            KairoTextButton(stringResource(R.string.timetable_delete_block), { onDelete(initial) }, Modifier.fillMaxWidth(), color = KairoTheme.colors.error)
        }
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 820)
@Composable
private fun BlockEditorFormPreview() {
    KairoTheme {
        SheetFrame { BlockEditorForm(initial = PreviewData.blocks.first(), roles = PreviewData.roles, hiddenRoleIds = emptySet(), onSave = {}, onDelete = {}) }
    }
}
