package com.kairo.app.ui.alarms

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kairo.app.R
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.AlarmHealthChecker
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.ui.theme.KairoColors
import com.kairo.app.ui.theme.KairoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Re-checked every time the screen resumes, i.e. right after the user returns from a settings page. */
@Composable
fun rememberAlarmHealth(nextAlarm: Instant?): AlarmHealth {
    val context = LocalContext.current
    var health by remember { mutableStateOf(AlarmHealth.ALL_GOOD) }
    var version by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    health = remember(version, nextAlarm) { AlarmHealthChecker.check(context, nextAlarm) }
    return health
}

/** Opens the right fix: the notification prompt where Android allows asking, otherwise the settings page. */
@Composable
fun rememberHealthFixer(): (HealthIssue) -> Unit {
    val context = LocalContext.current
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied before ("don't ask again"): the prompt returns at once, so go to settings instead.
        if (!granted) openSettings(context, HealthIssue.NOTIFICATIONS)
    }
    return { issue ->
        if (issue == HealthIssue.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings(context, issue)
        }
    }
}

private fun openSettings(context: Context, issue: HealthIssue) = context.startActivity(AlarmHealthChecker.fixIntent(context, issue))

/** Persistent while any alarm permission is missing (CLAUDE.md: never fail silently). */
@Composable
fun AlarmPermissionBanner(health: AlarmHealth, onFix: (HealthIssue) -> Unit, modifier: Modifier = Modifier) {
    val missing = health.missingPermissions
    if (missing.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = KairoColors.NeonMagenta)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.alarm_banner_title), style = MaterialTheme.typography.titleSmall)
            }
            missing.forEach { issue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(issueText(issue)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onFix(issue) }) { Text(stringResource(R.string.alarm_fix)) }
                }
            }
        }
    }
}

/** Settings card: every check, good or bad, plus the next scheduled alarm. */
@Composable
fun AlarmHealthCard(health: AlarmHealth, onFix: (HealthIssue) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HealthRow(R.string.alarm_health_exact, health.exactAlarms) { onFix(HealthIssue.EXACT_ALARMS) }
            HealthRow(R.string.alarm_health_full_screen, health.fullScreen) { onFix(HealthIssue.FULL_SCREEN) }
            HealthRow(R.string.alarm_health_notifications, health.notifications) { onFix(HealthIssue.NOTIFICATIONS) }
            HealthRow(R.string.alarm_health_battery, health.batteryUnrestricted) { onFix(HealthIssue.BATTERY) }
            HealthRow(R.string.alarm_health_volume, health.alarmVolumeAudible) { onFix(HealthIssue.ALARM_VOLUME) }
            Text(
                health.nextAlarm?.let { stringResource(R.string.alarm_health_next, formatInstant(it)) } ?: stringResource(R.string.alarm_health_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun HealthRow(label: Int, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = stringResource(if (ok) R.string.alarm_health_ok else R.string.alarm_health_problem),
            tint = if (ok) KairoColors.NeonLime else KairoColors.NeonMagenta,
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text(stringResource(R.string.alarm_fix)) }
    }
}

private fun issueText(issue: HealthIssue) = when (issue) {
    HealthIssue.EXACT_ALARMS -> R.string.alarm_issue_exact
    HealthIssue.FULL_SCREEN -> R.string.alarm_issue_full_screen
    HealthIssue.NOTIFICATIONS -> R.string.alarm_issue_notifications
    HealthIssue.BATTERY -> R.string.alarm_health_battery
    HealthIssue.ALARM_VOLUME -> R.string.alarm_health_volume
}

@Composable
fun formatInstant(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    return stringResource(
        R.string.alarm_day_time,
        local.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, com.kairo.app.ui.components.currentLocale()),
        local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
    )
}

private val PREVIEW_BROKEN = AlarmHealth(false, false, true, false, true, null)

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AlarmPermissionBannerPreview() {
    KairoTheme { AlarmPermissionBanner(PREVIEW_BROKEN, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AlarmHealthCardPreview() {
    KairoTheme { AlarmHealthCard(PREVIEW_BROKEN.copy(exactAlarms = true, nextAlarm = Instant.parse("2026-10-06T01:30:00Z")), {}) }
}
