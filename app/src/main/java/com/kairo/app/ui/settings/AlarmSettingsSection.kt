package com.kairo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.HealthIssue
import com.kairo.app.ui.alarms.AlarmHealthCard
import com.kairo.app.ui.design.KairoTheme

/** Alarm health at a glance plus a one-minute test alarm, to prove ringing works on this phone. */
@Composable
fun AlarmSettingsSection(health: AlarmHealth, onFix: (HealthIssue) -> Unit, onTestAlarm: () -> Unit, modifier: Modifier = Modifier) {
    var testScheduled by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_alarms), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        AlarmHealthCard(health, onFix)
        OutlinedButton(
            onClick = {
                onTestAlarm()
                testScheduled = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_test_alarm)) }
        if (testScheduled) {
            Text(stringResource(R.string.settings_test_alarm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AlarmSettingsSectionPreview() {
    KairoTheme { AlarmSettingsSection(AlarmHealth.ALL_GOOD, {}, {}) }
}
