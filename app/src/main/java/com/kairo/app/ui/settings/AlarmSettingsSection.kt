package com.kairo.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.SecondaryButton

/** A one-minute test alarm, to prove ringing works on this phone. Health checks live in Permissions and health. */
@Composable
fun AlarmSettingsSection(onTestAlarm: () -> Unit, modifier: Modifier = Modifier) {
    var testScheduled by remember { mutableStateOf(false) }
    GlassCard(modifier) {
        Text(stringResource(R.string.settings_test_alarm_intro), style = KairoTheme.type.bodyMedium, color = KairoTheme.colors.textSecondary)
        SecondaryButton(
            stringResource(R.string.settings_test_alarm),
            onClick = {
                onTestAlarm()
                testScheduled = true
            },
            icon = Icons.Outlined.NotificationsActive,
            modifier = Modifier.fillMaxWidth(),
        )
        if (testScheduled) {
            Text(stringResource(R.string.settings_test_alarm_hint), style = KairoTheme.type.bodySmall, color = KairoTheme.colors.success)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun AlarmSettingsSectionPreview() {
    KairoTheme { AlarmSettingsSection({}) }
}
