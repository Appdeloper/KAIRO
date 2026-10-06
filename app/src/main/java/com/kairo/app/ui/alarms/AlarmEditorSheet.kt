package com.kairo.app.ui.alarms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.kairo.app.R
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.ChoicePill
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.ListRow
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.SegmentedControl
import com.kairo.app.ui.design.components.SheetFrame
import com.kairo.app.ui.design.components.SliderRow
import com.kairo.app.ui.design.components.ToggleRow
import com.kairo.app.util.formatMinuteOfDay
import kotlin.math.roundToInt

private val SnoozeOptions = listOf(5, 10, 15)
private val MaxSnoozeOptions = listOf(0, 1, 3, 5)
private val OffsetOptions = listOf(10, 15, 30, 60)
private const val MAX_RAMP_SECONDS = 60f

@Composable
fun AlarmEditorSheet(initial: Alarm, blocks: List<FixedBlock>, onDismiss: () -> Unit, onSave: (Alarm) -> Unit, onDelete: (Alarm) -> Unit) {
    KairoBottomSheet(onDismissRequest = onDismiss) {
        AlarmEditorForm(initial, blocks, onSave, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorForm(initial: Alarm, blocks: List<FixedBlock>, onSave: (Alarm) -> Unit, onDelete: (Alarm) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var pickingTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = KairoTheme.colors
    val ringtonePicker = rememberLauncherForActivityResult(PickAlarmRingtone()) { picked ->
        if (picked != null) draft = draft.copy(ringtoneUri = picked.toString())
    }
    val block = blocks.firstOrNull { it.id == draft.linkedBlockId }
    val minuteOfDay = AlarmPlans.resolve(draft, block).minuteOfDay
    val types = listOf(AlarmType.WAKE to R.string.alarm_type_wake, AlarmType.BLOCK to R.string.alarm_type_block, AlarmType.ONE_SHOT to R.string.alarm_type_once)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(if (initial.id == 0L) R.string.alarm_add else R.string.alarm_edit), style = KairoTheme.type.headlineSmall)
        // The time is the point of an alarm, so it's the biggest thing in the sheet.
        Text(
            formatMinuteOfDay(context, if (draft.type == AlarmType.BLOCK) minuteOfDay else draft.hour * 60 + draft.minute),
            style = KairoTheme.numbers.hero,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (draft.type != AlarmType.BLOCK) Modifier.clickable(onClickLabel = stringResource(R.string.alarm_change_time)) { pickingTime = true } else Modifier),
            textAlign = TextAlign.Center,
        )
        SegmentedControl(
            options = types.map { stringResource(it.second) },
            selectedIndex = types.indexOfFirst { it.first == draft.type },
            onSelect = { i ->
                val type = types[i].first
                draft = when (type) {
                    AlarmType.ONE_SHOT -> draft.copy(type = type, daysOfWeekMask = AlarmDays.ONE_SHOT)
                    AlarmType.WAKE -> draft.copy(type = type, daysOfWeekMask = draft.daysOfWeekMask.takeIf { it != 0 } ?: AlarmDays.WEEKDAYS, linkedBlockId = null)
                    AlarmType.BLOCK -> draft.copy(type = type, linkedBlockId = draft.linkedBlockId ?: blocks.firstOrNull()?.id, offsetMinutesBeforeBlock = draft.offsetMinutesBeforeBlock ?: 15)
                }
            },
        )
        KairoTextField(value = draft.label, onValueChange = { draft = draft.copy(label = it) }, label = stringResource(R.string.alarm_label))
        if (draft.type == AlarmType.BLOCK) {
            BlockPicker(blocks, draft.linkedBlockId, draft.offsetMinutesBeforeBlock ?: 15) { id, offset ->
                draft = draft.copy(linkedBlockId = id, offsetMinutesBeforeBlock = offset)
            }
        }
        if (draft.type == AlarmType.WAKE) {
            SectionHeader(stringResource(R.string.alarm_repeat))
            DayChips(draft.daysOfWeekMask, onToggle = { draft = draft.copy(daysOfWeekMask = AlarmDays.toggle(draft.daysOfWeekMask, it)) })
        }
        SectionHeader(stringResource(R.string.alarm_sound_section))
        GlassCard(level = GlassLevel.ONE, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            ListRow(
                stringResource(R.string.alarm_sound),
                subtitle = ringtoneTitle(context, draft.ringtoneUri),
                icon = Icons.Outlined.MusicNote,
                onClick = { ringtonePicker.launch(draft.ringtoneUri?.toUri()) },
            )
            ToggleRow(stringResource(R.string.alarm_vibrate), draft.vibrate, { draft = draft.copy(vibrate = it) })
            ToggleRow(stringResource(R.string.alarm_open_briefing), draft.openBriefingOnDismiss, { draft = draft.copy(openBriefingOnDismiss = it) })
            SliderRow(
                stringResource(R.string.alarm_ramp_title),
                stringResource(R.string.alarm_ramp_value, draft.rampUpSeconds),
                draft.rampUpSeconds.toFloat(),
                { draft = draft.copy(rampUpSeconds = it.roundToInt()) },
                valueRange = 0f..MAX_RAMP_SECONDS,
            )
        }
        SectionHeader(stringResource(R.string.alarm_snooze_length))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SnoozeOptions.forEach { m -> ChoicePill(stringResource(R.string.duration_minutes, m), draft.snoozeMinutes == m, { draft = draft.copy(snoozeMinutes = m) }) }
        }
        SectionHeader(stringResource(R.string.alarm_max_snoozes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MaxSnoozeOptions.forEach { n -> ChoicePill(n.toString(), draft.maxSnoozes == n, { draft = draft.copy(maxSnoozes = n) }) }
        }
        Spacer(Modifier.height(Spacing.xs))
        val valid = draft.type != AlarmType.WAKE || draft.daysOfWeekMask != 0
        PrimaryButton(
            stringResource(if (initial.id == 0L) R.string.alarm_add else R.string.action_save),
            onClick = { onSave(draft.copy(label = draft.label.trim(), snoozeCount = 0, snoozedUntilMillis = null)) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!valid) Text(stringResource(R.string.alarm_pick_a_day), style = KairoTheme.type.bodySmall, color = colors.warning)
        if (initial.id != 0L) {
            KairoTextButton(stringResource(R.string.alarm_delete), { onDelete(initial) }, Modifier.fillMaxWidth(), color = colors.error)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockPicker(blocks: List<FixedBlock>, selectedId: Long?, offset: Int, onChange: (Long?, Int) -> Unit) {
    if (blocks.isEmpty()) {
        Text(stringResource(R.string.alarm_no_blocks), style = KairoTheme.type.bodyMedium, color = KairoTheme.colors.textSecondary)
        return
    }
    val locale = com.kairo.app.ui.components.currentLocale()
    SectionHeader(stringResource(R.string.alarm_which_class))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        blocks.forEach { b ->
            ChoicePill(
                stringResource(R.string.alarm_block_chip, java.time.DayOfWeek.of(b.dayOfWeek).getDisplayName(java.time.format.TextStyle.SHORT, locale), b.title),
                b.id == selectedId,
                { onChange(b.id, offset) },
            )
        }
    }
    SectionHeader(stringResource(R.string.alarm_before_block))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OffsetOptions.forEach { m -> ChoicePill(stringResource(R.string.duration_minutes, m), offset == m, { onChange(selectedId, m) }) }
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 1200)
@Composable
private fun AlarmEditorFormPreview() {
    KairoTheme {
        SheetFrame { AlarmEditorForm(Alarm(hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS, label = "Wake up"), PreviewData.blocks, {}, {}) }
    }
}

/** For screenshot tests. */
@Composable
fun AlarmEditorPreviewContent() {
    AlarmEditorForm(Alarm(id = 1, hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS, label = "Wake up"), PreviewData.blocks, {}, {})
}
