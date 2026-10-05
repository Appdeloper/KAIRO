package com.kairo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.ai.AiSettings
import com.kairo.app.ui.alarms.AlarmsViewModel
import com.kairo.app.ui.shake.ShakeSettingsSection
import com.kairo.app.ui.focus.FocusSettingsSection
import com.kairo.app.ui.alarms.alarmsViewModelFactory
import com.kairo.app.ui.alarms.rememberAlarmHealth
import com.kairo.app.ui.alarms.rememberHealthFixer
import com.kairo.app.data.local.Role
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RoleDot
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.onboarding.ProfileForm
import com.kairo.app.ui.onboarding.ProfileViewModel
import com.kairo.app.ui.theme.KairoTheme

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel = viewModel(factory = containerFactory { ProfileViewModel(it.userPrefsRepository, it.roleRepository) }),
    aiViewModel: AiSettingsViewModel = viewModel(factory = containerFactory { AiSettingsViewModel(it.aiSettingsRepository) }),
    alarmsViewModel: AlarmsViewModel = viewModel(factory = alarmsViewModelFactory()),
) {
    val alarmState by alarmsViewModel.state.collectAsStateWithLifecycle()
    val alarmHealth = rememberAlarmHealth(alarmState.next?.second)
    val fixAlarm = rememberHealthFixer()
    val testLabel = stringResource(R.string.alarm_test_label)
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val ai by aiViewModel.settings.collectAsStateWithLifecycle()
    // Wait for stored values so neither form starts from placeholder values.
    val loadedPrefs = prefs
    val loadedAi = ai
    if (loadedPrefs != null && loadedAi != null) {
        SettingsContent(
            prefs = loadedPrefs,
            roles = roles,
            ai = loadedAi,
            onSave = viewModel::save,
            onSaveAi = aiViewModel::save,
            alarmSection = {
                AlarmSettingsSection(alarmHealth, fixAlarm, onTestAlarm = { alarmsViewModel.scheduleTestAlarm(testLabel) })
                ShakeSettingsSection(alarmHealth, fixAlarm)
                FocusSettingsSection()
            },
        )
    }
}

@Composable
fun SettingsContent(
    prefs: UserPrefs,
    roles: List<Role>,
    ai: AiSettings,
    onSave: (name: String, wakeMinute: Int, sleepMinute: Int) -> Unit,
    onSaveAi: (AiSettings) -> Unit,
    modifier: Modifier = Modifier,
    alarmSection: @Composable () -> Unit = {},
) {
    var name by rememberSaveable(prefs) { mutableStateOf(prefs.firstName) }
    var wake by rememberSaveable(prefs) { mutableIntStateOf(prefs.wakeMinute) }
    var sleep by rememberSaveable(prefs) { mutableIntStateOf(prefs.sleepMinute) }
    val dirty = name != prefs.firstName || wake != prefs.wakeMinute || sleep != prefs.sleepMinute

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ProfileForm(
            name = name,
            onNameChange = { name = it },
            wakeMinute = wake,
            onWakeChange = { wake = it },
            sleepMinute = sleep,
            onSleepChange = { sleep = it },
            showNameError = name.isBlank(),
        )
        Button(onClick = { onSave(name, wake, sleep) }, enabled = dirty && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (dirty) R.string.action_save else R.string.settings_saved))
        }

        alarmSection()
        AiSettingsSection(saved = ai, onSave = onSaveAi)
        QuickAccessSection()

        Text(stringResource(R.string.settings_roles), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        roles.forEach { role ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoleDot(role, size = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(role.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.settings_role_budget, role.dailyBudgetMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun SettingsContentPreview() {
    KairoTheme {
        SettingsContent(
            prefs = UserPrefs(firstName = "Aarav", onboardingDone = true),
            roles = PreviewData.roles,
            ai = AiSettings(),
            onSave = { _, _, _ -> },
            onSaveAi = {},
        )
    }
}
