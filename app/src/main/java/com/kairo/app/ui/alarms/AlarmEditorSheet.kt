package com.kairo.app.ui.alarms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.util.formatMinuteOfDay
import kotlin.math.roundToInt

private val SnoozeOptions = listOf(5, 10, 15)
private val MaxSnoozeOptions = listOf(0, 1, 3, 5)
private val OffsetOptions = listOf(10, 15, 30, 60)
private const val MAX_RAMP_SECONDS = 60f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(initial: Alarm, blocks: List<FixedBlock>, onDismiss: () -> Unit, onSave: (Alarm) -> Unit, onDelete: (Alarm) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AlarmEditorForm(initial, blocks, onSave, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorForm(initial: Alarm, blocks: List<FixedBlock>, onSave: (Alarm) -> Unit, onDelete: (Alarm) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var pickingTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ringtonePicker = rememberLauncherForActivityResult(PickAlarmRingtone()) { picked ->
        if (picked != null) draft = draft.copy(ringtoneUri = picked.toString())
    }
    val block = blocks.firstOrNull { it.id == draft.linkedBlockId }
    val minuteOfDay = AlarmPlans.resolve(draft, block).minuteOfDay

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(if (initial.id == 0L) R.string.alarm_add else R.string.alarm_edit), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = draft.label,
            onValueChange = { draft = draft.copy(label = it) },
            label = { Text(stringResource(R.string.alarm_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TypeSelector(draft.type) { type ->
            draft = when (type) {
                AlarmType.ONE_SHOT -> draft.copy(type = type, daysOfWeekMask = AlarmDays.ONE_SHOT)
                AlarmType.WAKE -> draft.copy(type = type, daysOfWeekMask = draft.daysOfWeekMask.takeIf { it != 0 } ?: AlarmDays.WEEKDAYS, linkedBlockId = null)
                AlarmType.BLOCK -> draft.copy(type = type, linkedBlockId = draft.linkedBlockId ?: blocks.firstOrNull()?.id, offsetMinutesBeforeBlock = draft.offsetMinutesBeforeBlock ?: 15)
            }
        }
        if (draft.type == AlarmType.BLOCK) {
            BlockPicker(blocks, draft.linkedBlockId, draft.offsetMinutesBeforeBlock ?: 15) { id, offset ->
                draft = draft.copy(linkedBlockId = id, offsetMinutesBeforeBlock = offset)
            }
            Text(stringResource(R.string.alarm_rings_at, formatMinuteOfDay(context, minuteOfDay)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedButton(onClick = { pickingTime = true }) {
                Text(formatMinuteOfDay(context, draft.hour * 60 + draft.minute), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (draft.type == AlarmType.WAKE) {
            DayChips(draft.daysOfWeekMask, onToggle = { draft = draft.copy(daysOfWeekMask = AlarmDays.toggle(draft.daysOfWeekMask, it)) })
        }
        OutlinedButton(onClick = { ringtonePicker.launch(draft.ringtoneUri?.toUri()) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.alarm_ringtone, ringtoneTitle(context, draft.ringtoneUri)))
        }
        SwitchRow(R.string.alarm_vibrate, draft.vibrate) { draft = draft.copy(vibrate = it) }
        SwitchRow(R.string.alarm_open_briefing, draft.openBriefingOnDismiss) { draft = draft.copy(openBriefingOnDismiss = it) }
        Text(stringResource(R.string.alarm_ramp, draft.rampUpSeconds), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = draft.rampUpSeconds.toFloat(),
            onValueChange = { draft = draft.copy(rampUpSeconds = it.roundToInt()) },
            valueRange = 0f..MAX_RAMP_SECONDS,
        )
        Text(stringResource(R.string.alarm_snooze_length), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnoozeOptions.forEach { m ->
                FilterChip(selected = draft.snoozeMinutes == m, onClick = { draft = draft.copy(snoozeMinutes = m) }, label = { Text(stringResource(R.string.duration_minutes, m)) })
            }
        }
        Text(stringResource(R.string.alarm_max_snoozes), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaxSnoozeOptions.forEach { n ->
                FilterChip(selected = draft.maxSnoozes == n, onClick = { draft = draft.copy(maxSnoozes = n) }, label = { Text(n.toString()) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (initial.id != 0L) {
                TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.weight(1f))
            val valid = draft.type != AlarmType.WAKE || draft.daysOfWeekMask != 0
            Button(onClick = { onSave(draft.copy(label = draft.label.trim(), snoozeCount = 0, snoozedUntilMillis = null)) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            initialMinute = draft.hour * 60 + draft.minute,
            onDismiss = { pickingTime = false },
            onConfirm = { m ->
                draft = draft.copy(hour = m / 60, minute = m % 60)
                pickingTime = false
            },
        )
    }
}

@Composable
private fun TypeSelector(selected: AlarmType, onSelect: (AlarmType) -> Unit) {
    val types = listOf(AlarmType.WAKE to R.string.alarm_type_wake, AlarmType.BLOCK to R.string.alarm_type_block, AlarmType.ONE_SHOT to R.string.alarm_type_once)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { i, (type, label) ->
            SegmentedButton(selected = selected == type, onClick = { onSelect(type) }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) {
                Text(stringResource(label))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockPicker(blocks: List<FixedBlock>, selectedId: Long?, offset: Int, onChange: (Long?, Int) -> Unit) {
    if (blocks.isEmpty()) {
        Text(stringResource(R.string.alarm_no_blocks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val locale = com.kairo.app.ui.components.currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            FilterChip(
                selected = b.id == selectedId,
                onClick = { onChange(b.id, offset) },
                label = { Text(stringResource(R.string.alarm_block_chip, java.time.DayOfWeek.of(b.dayOfWeek).getDisplayName(java.time.format.TextStyle.SHORT, locale), b.title)) },
            )
        }
    }
    Text(stringResource(R.string.alarm_before_block), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OffsetOptions.forEach { m ->
            FilterChip(selected = offset == m, onClick = { onChange(selectedId, m) }, label = { Text(stringResource(R.string.duration_minutes, m)) })
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ringtoneTitle(context: Context, uri: String?): String {
    val fallback = stringResource(R.string.alarm_ringtone_default)
    return remember(uri) {
        uri?.let { runCatching { RingtoneManager.getRingtone(context, it.toUri())?.getTitle(context) }.getOrNull() } ?: fallback
    }
}

/** System alarm-sound picker; returns null if the user backs out. */
private class PickAlarmRingtone : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        @Suppress("DEPRECATION")
        return intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F16, heightDp = 900)
@Composable
private fun AlarmEditorFormPreview() {
    KairoTheme {
        AlarmEditorForm(Alarm(hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS, label = "Wake up"), PreviewData.blocks, {}, {})
    }
}
