package com.kairo.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kairo.app.R
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.service.focus.FocusPromotion
import com.kairo.app.service.focus.PromotionStatus
import com.kairo.app.ui.alarms.AlarmHealthRows
import com.kairo.app.ui.alarms.HealthRow
import com.kairo.app.ui.alarms.formatInstant
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.focus.openSafely
import com.kairo.app.ui.shake.ShakeHealthGroup

/**
 * Every permission and health check in one place, each with its status and a Fix button.
 * Re-checked on resume, i.e. right after the user comes back from a system settings page.
 */
@Composable
fun PermissionsHealthSection(alarmHealth: AlarmHealth, onFix: (HealthIssue) -> Unit) {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val micGranted = remember(resumes) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val promotion = remember(resumes) { FocusPromotion.status(context) }
    val micPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // "Don't ask again" returns at once: send them to app info where the switch lives.
        if (!granted) context.openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        resumes++
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GlassCard {
            AlarmHealthRows(alarmHealth, onFix)
            HealthRow(R.string.permission_mic_row, micGranted) { micPrompt.launch(Manifest.permission.RECORD_AUDIO) }
            if (promotion != PromotionStatus.NOT_SUPPORTED) {
                HealthRow(R.string.permission_live_updates_row, promotion == PromotionStatus.ALLOWED) {
                    context.openSafely(FocusPromotion.settingsIntent(context))
                }
            }
            Text(
                alarmHealth.nextAlarm?.let { stringResource(R.string.alarm_health_next, formatInstant(it)) } ?: stringResource(R.string.alarm_health_none),
                style = KairoTheme.type.bodySmall,
                color = KairoTheme.colors.textSecondary,
            )
        }
        ShakeHealthGroup(alarmHealth, onFix)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun PermissionsHealthPreview() {
    KairoTheme {
        GlassCard { AlarmHealthRows(AlarmHealth(false, true, true, false, true, null), {}) }
    }
}
