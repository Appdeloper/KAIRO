package com.kairo.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.data.local.Role
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.ChoicePill
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.LanePicker
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.SegmentedControl
import com.kairo.app.ui.design.components.SheetFrame
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DurationOptions = listOf(15, 30, 45, 60, 90, 120)
private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun AddTaskSheet(roles: List<Role>, onDismiss: () -> Unit, onSave: (NewTaskInput) -> Unit, hiddenRoleIds: Set<Long> = emptySet()) {
    KairoBottomSheet(onDismissRequest = onDismiss) {
        AddTaskForm(roles, hiddenRoleIds, onSave)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskForm(roles: List<Role>, hiddenRoleIds: Set<Long>, onSave: (NewTaskInput) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var roleId by rememberSaveable { mutableStateOf(roles.firstOrNull { it.id !in hiddenRoleIds }?.id ?: roles.firstOrNull()?.id) }
    var duration by rememberSaveable { mutableIntStateOf(30) }
    var priority by rememberSaveable { mutableIntStateOf(3) }
    var deadline by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.tasks_add), style = KairoTheme.type.headlineSmall)
        KairoTextField(
            value = title,
            onValueChange = { title = it },
            label = stringResource(R.string.task_title_label),
            error = if (showErrors && title.isBlank()) stringResource(R.string.task_error_title) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        SectionHeader(stringResource(R.string.block_lane_label))
        LanePicker(roles = roles, selectedId = roleId, onSelect = { roleId = it }, hiddenIds = hiddenRoleIds)

        SectionHeader(stringResource(R.string.task_duration_label))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DurationOptions.forEach { minutes ->
                ChoicePill(stringResource(R.string.task_duration_minutes, minutes), duration == minutes, { duration = minutes })
            }
        }

        SectionHeader(stringResource(R.string.task_priority_label))
        SegmentedControl(
            options = (1..4).map { stringResource(R.string.task_priority_short, it) },
            selectedIndex = priority - 1,
            onSelect = { priority = it + 1 },
        )
        Text(stringResource(R.string.task_priority_help), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.textTertiary)

        SectionHeader(stringResource(R.string.task_deadline_label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                deadline?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                    ?: stringResource(R.string.task_deadline_none),
                onClick = { pickingDate = true },
            )
            if (deadline != null) KairoTextButton(stringResource(R.string.task_deadline_clear), { deadline = null })
        }
        Spacer(Modifier.height(Spacing.xs))
        PrimaryButton(
            stringResource(R.string.tasks_add),
            onClick = {
                val role = roleId
                if (title.isBlank() || role == null) showErrors = true else onSave(NewTaskInput(title, role, duration, priority, deadline))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingDate) {
        // DatePicker works in UTC millis at midnight, so integer division gives the exact epoch day.
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = deadline?.times(MILLIS_PER_DAY))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                KairoTextButton(stringResource(R.string.action_ok), {
                    pickerState.selectedDateMillis?.let { deadline = it / MILLIS_PER_DAY }
                    pickingDate = false
                })
            },
            dismissButton = { KairoTextButton(stringResource(R.string.action_cancel), { pickingDate = false }, color = KairoTheme.colors.textSecondary) },
        ) { DatePicker(state = pickerState) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 860)
@Composable
private fun AddTaskFormPreview() {
    KairoTheme { SheetFrame { AddTaskForm(roles = PreviewData.roles, hiddenRoleIds = emptySet(), onSave = {}) } }
}
