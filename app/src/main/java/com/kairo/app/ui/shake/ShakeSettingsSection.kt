package com.kairo.app.ui.shake

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.service.shake.OemFamily
import com.kairo.app.service.shake.OemGuide
import com.kairo.app.service.shake.OemGuides
import com.kairo.app.service.shake.RestartCounter
import com.kairo.app.service.shake.ShakeControl
import com.kairo.app.service.shake.ShakeDetector
import com.kairo.app.service.shake.ShakeService
import com.kairo.app.service.shake.ShakeStatus
import com.kairo.app.ui.alarms.HealthRow
import com.kairo.app.ui.alarms.formatInstant
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.SliderRow
import com.kairo.app.ui.design.components.TimeField
import com.kairo.app.ui.design.components.ToggleRow
import com.kairo.app.util.formatMinuteOfDay
import java.time.Instant
import java.time.LocalDate

fun shakeViewModelFactory() = containerFactory { ShakeViewModel(it.shakePrefsRepository) }

private enum class HourField { START, END }

/** Settings > Shake: the switch, sensitivity with a live meter, when it listens, and an honest note. */
@Composable
fun ShakeSettingsSection(viewModel: ShakeViewModel = viewModel(factory = shakeViewModelFactory())) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf<HourField?>(null) }
    val s = state.settings

    GlassCard {
        ToggleRow(stringResource(R.string.shake_toggle), s.enabled, { on ->
            viewModel.setEnabled(on)
            // Started here, from a visible screen: the documented way to start a foreground service.
            if (on) ShakeControl.start(context) else ShakeControl.stop(context)
        }, icon = Icons.Outlined.Vibration)
        Text(stringResource(R.string.shake_honest_note), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.textSecondary)
        // Slider is "sensitivity": right = more sensitive = lower threshold.
        SliderRow(
            stringResource(R.string.shake_sensitivity),
            stringResource(R.string.shake_sensitivity_value, ((ShakeDetector.MAX_THRESHOLD - s.threshold) / (ShakeDetector.MAX_THRESHOLD - ShakeDetector.MIN_THRESHOLD) * 100).toInt()),
            ShakeDetector.MAX_THRESHOLD + ShakeDetector.MIN_THRESHOLD - s.threshold,
            { viewModel.setThreshold(ShakeDetector.MAX_THRESHOLD + ShakeDetector.MIN_THRESHOLD - it) },
            valueRange = ShakeDetector.MIN_THRESHOLD..ShakeDetector.MAX_THRESHOLD,
        )
        ShakeTestMeter(threshold = s.threshold)
        Text(stringResource(R.string.shake_active_hours), style = KairoTheme.type.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TimeField(stringResource(R.string.shake_from), formatMinuteOfDay(context, s.activeStartMinute), { picking = HourField.START }, Modifier.weight(1f))
            TimeField(stringResource(R.string.shake_until), formatMinuteOfDay(context, s.activeEndMinute), { picking = HourField.END }, Modifier.weight(1f))
        }
        ToggleRow(stringResource(R.string.shake_only_charging), s.onlyWhileCharging, viewModel::setOnlyWhileCharging)
    }

    picking?.let { field ->
        TimePickerDialog(
            initialMinute = if (field == HourField.START) s.activeStartMinute else s.activeEndMinute,
            onDismiss = { picking = null },
            onConfirm = { m ->
                if (field == HourField.START) viewModel.setActiveHours(m, s.activeEndMinute) else viewModel.setActiveHours(s.activeStartMinute, m)
                picking = null
            },
        )
    }
}

/**
 * Live magnitude while Settings is on screen (sensor registered only while resumed). The running
 * service is told not to launch meanwhile, so testing doesn't keep opening the briefing.
 */
@Composable
private fun ShakeTestMeter(threshold: Float) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var magnitude by remember { mutableFloatStateOf(0f) }
    var detections by remember { mutableIntStateOf(0) }
    val detector = remember { ShakeDetector() }
    detector.threshold = threshold

    DisposableEffect(resumed) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (detector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)) detections++
                magnitude = detector.lastMagnitude
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (resumed && accelerometer != null) {
            ShakeService.suppressLaunch = true
            sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensors.unregisterListener(listener)
            ShakeService.suppressLaunch = false
        }
    }

    val colors = KairoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LinearProgressIndicator(
            progress = { (magnitude / ShakeDetector.MAX_THRESHOLD).coerceIn(0f, 1f) },
            color = if (magnitude >= threshold) colors.success else colors.primary,
            trackColor = colors.surface3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.shake_meter, magnitude, threshold, detections), style = KairoTheme.numbers.small, color = colors.textSecondary)
    }
}

/** Shake's part of Settings > Permissions and health: is it running, can it open the briefing, will the phone kill it. */
@Composable
fun ShakeHealthGroup(alarmHealth: AlarmHealth, onFix: (HealthIssue) -> Unit, viewModel: ShakeViewModel = viewModel(factory = shakeViewModelFactory())) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (status, _) = rememberShakeStatus()
    val s = state.settings
    ShakeHealthCard(status, s.restartEpochDay, s.restartCount, s.lastHeartbeatMillis, s.lastLaunchPath, alarmHealth, onFix)
}

@Composable
internal fun ShakeHealthCard(
    status: ShakeStatus,
    restartDay: Long?,
    restartCount: Int,
    lastHeartbeat: Long?,
    lastPath: String?,
    alarmHealth: AlarmHealth,
    onFix: (HealthIssue) -> Unit,
) {
    val context = LocalContext.current
    val colors = KairoTheme.colors
    var overlayVersion by remember { mutableIntStateOf(0) }
    val overlayAllowed = remember(overlayVersion, alarmHealth) { Settings.canDrawOverlays(context) }
    val restarts = RestartCounter.countToday(restartDay, restartCount, LocalDate.now().toEpochDay())
    LaunchedEffect(alarmHealth) { overlayVersion++ }

    GlassCard {
        HealthRow(
            when (status) {
                ShakeStatus.ARMED -> R.string.shake_health_armed
                ShakeStatus.STOPPED -> R.string.shake_health_stopped
                ShakeStatus.OFF -> R.string.shake_health_off
            },
            ok = status != ShakeStatus.STOPPED,
        ) { ShakeControl.start(context) }
        HealthRow(R.string.shake_health_overlay, overlayAllowed) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Text(stringResource(R.string.shake_health_restarts, restarts), style = KairoTheme.type.bodySmall, color = colors.textSecondary)
        Text(
            lastHeartbeat?.let { stringResource(R.string.shake_health_heartbeat, formatInstant(Instant.ofEpochMilli(it))) }
                ?: stringResource(R.string.shake_health_no_heartbeat),
            style = KairoTheme.type.bodySmall,
            color = colors.textSecondary,
        )
        lastPath?.let { Text(stringResource(R.string.shake_health_last_path, it), style = KairoTheme.type.bodySmall, color = colors.textTertiary) }
        OemGuides.forDevice(Build.MANUFACTURER, Build.BRAND)?.let { OemFixCard(it) }
    }
}

@Composable
private fun OemFixCard(guide: OemGuide) {
    val context = LocalContext.current
    GlassCard(level = GlassLevel.TWO) {
        Text(stringResource(R.string.shake_oem_title, Build.MANUFACTURER), style = KairoTheme.type.titleSmall)
        Text(stringResource(oemSteps(guide.family)), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.textSecondary)
        SecondaryButton(stringResource(R.string.shake_oem_open), { openOemSettings(context, guide) })
    }
}

private fun oemSteps(family: OemFamily) = when (family) {
    OemFamily.XIAOMI -> R.string.oem_steps_xiaomi
    OemFamily.VIVO -> R.string.oem_steps_vivo
    OemFamily.OPPO -> R.string.oem_steps_oppo
    OemFamily.ONEPLUS -> R.string.oem_steps_oneplus
    OemFamily.SAMSUNG -> R.string.oem_steps_samsung
}

/** Tries each known vendor screen; these aren't public APIs and vary by ROM, so app info is the floor. */
private fun openOemSettings(context: Context, guide: OemGuide) {
    for (target in guide.targets) {
        val intent = Intent().setComponent(ComponentName(target.packageName, target.className)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            continue
        } catch (e: SecurityException) {
            continue
        }
    }
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun ShakeHealthCardPreview() {
    KairoTheme {
        ShakeHealthCard(ShakeStatus.STOPPED, LocalDate.now().toEpochDay(), 2, System.currentTimeMillis() - 600_000, "notification(overlay-blocked)", AlarmHealth.ALL_GOOD, {})
    }
}
