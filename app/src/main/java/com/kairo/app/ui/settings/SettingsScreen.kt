package com.kairo.app.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.ai.AiSettings
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ui.alarms.AlarmsViewModel
import com.kairo.app.ui.alarms.alarmsViewModelFactory
import com.kairo.app.ui.alarms.rememberAlarmHealth
import com.kairo.app.ui.alarms.rememberHealthFixer
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.KairoScaffold
import com.kairo.app.ui.design.components.KairoSnackbarHost
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.TimeField
import com.kairo.app.ui.design.components.TopBar
import com.kairo.app.ui.focus.FocusSettingsSection
import com.kairo.app.ui.onboarding.LaneEditor
import com.kairo.app.ui.onboarding.ProfileViewModel
import com.kairo.app.ui.shake.ShakeSettingsSection
import com.kairo.app.util.formatMinuteOfDay

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel = viewModel(factory = containerFactory { ProfileViewModel(it.userPrefsRepository, it.roleRepository) }),
    aiViewModel: AiSettingsViewModel = viewModel(factory = containerFactory { AiSettingsViewModel(it.aiSettingsRepository) }),
    alarmsViewModel: AlarmsViewModel = viewModel(factory = alarmsViewModelFactory()),
    settings: SettingsViewModel = viewModel(
        factory = containerFactory { c ->
            SettingsViewModel(c.userPrefsRepository, c.roleRepository, c::loadSampleWeek, c::exportData, c::resetAllData)
        },
    ),
    aboutExtra: @Composable () -> Unit = {},
) {
    val alarmState by alarmsViewModel.state.collectAsStateWithLifecycle()
    val alarmHealth = rememberAlarmHealth(alarmState.next?.second)
    val fixAlarm = rememberHealthFixer()
    val testLabel = stringResource(R.string.alarm_test_label)
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val ai by aiViewModel.settings.collectAsStateWithLifecycle()
    val lanes by settings.lanes.collectAsStateWithLifecycle()
    val hidden by settings.hidden.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val versionLabel = rememberVersionLabel()
    LaunchedEffect(settings) {
        settings.events.collect { event ->
            when (event) {
                is DataEvent.SampleLoaded -> snackbar.showSnackbar(resources.getString(if (event.loaded) R.string.sample_loaded else R.string.sample_already_loaded))
                is DataEvent.Exported -> shareExport(context, event.json)
                DataEvent.ResetDone -> Unit // Onboarding replaces this screen as soon as the profile is gone.
            }
        }
    }
    // Wait for stored values so neither form starts from placeholder values.
    val loadedPrefs = prefs
    val loadedAi = ai
    if (loadedPrefs == null || loadedAi == null) return
    KairoScaffold(snackbarHost = { KairoSnackbarHost(snackbar) }, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
        SettingsContent(
            prefs = loadedPrefs,
            ai = loadedAi,
            onSave = viewModel::save,
            onSaveAi = aiViewModel::save,
            modifier = Modifier.padding(padding),
            lanes = { LaneEditor(lanes, hidden, settings::rename, settings::setHidden) },
            alarms = { AlarmSettingsSection(onTestAlarm = { alarmsViewModel.scheduleTestAlarm(testLabel) }) },
            shake = { ShakeSettingsSection() },
            focus = { FocusSettingsSection() },
            health = { PermissionsHealthSection(alarmHealth, fixAlarm) },
            data = { DataSection(settings::loadSample, settings::export, settings::reset) },
            about = { AboutSection(versionLabel, aboutExtra) },
        )
    }
}

/** Grouped settings with clear headers. Each section's content is a slot so previews can stand in for live ones. */
@Composable
fun SettingsContent(
    prefs: UserPrefs,
    ai: AiSettings,
    onSave: (name: String, wakeMinute: Int, sleepMinute: Int) -> Unit,
    onSaveAi: (AiSettings) -> Unit,
    modifier: Modifier = Modifier,
    lanes: @Composable () -> Unit = {},
    alarms: @Composable () -> Unit = {},
    shake: @Composable () -> Unit = {},
    focus: @Composable () -> Unit = {},
    health: @Composable () -> Unit = {},
    data: @Composable () -> Unit = {},
    about: @Composable () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(modifier.fillMaxSize().verticalScroll(scrollState)) {
        TopBar(stringResource(R.string.nav_settings))
        Column(
            Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.bottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader(stringResource(R.string.settings_profile))
            ProfileCard(prefs, onSave)
            SectionHeader(stringResource(R.string.settings_lanes))
            lanes()
            SectionHeader(stringResource(R.string.settings_ai))
            AiSettingsSection(saved = ai, onSave = onSaveAi)
            SectionHeader(stringResource(R.string.settings_alarms))
            alarms()
            SectionHeader(stringResource(R.string.settings_shake))
            shake()
            SectionHeader(stringResource(R.string.settings_focus))
            focus()
            SectionHeader(stringResource(R.string.settings_entry_points))
            QuickAccessSection()
            SectionHeader(stringResource(R.string.settings_health))
            health()
            SectionHeader(stringResource(R.string.settings_data))
            data()
            SectionHeader(stringResource(R.string.settings_about))
            about()
        }
    }
}

private enum class ProfileTime { WAKE, SLEEP }

@Composable
private fun ProfileCard(prefs: UserPrefs, onSave: (String, Int, Int) -> Unit) {
    var name by rememberSaveable(prefs) { mutableStateOf(prefs.firstName) }
    var wake by rememberSaveable(prefs) { mutableIntStateOf(prefs.wakeMinute) }
    var sleep by rememberSaveable(prefs) { mutableIntStateOf(prefs.sleepMinute) }
    var picking by remember { mutableStateOf<ProfileTime?>(null) }
    val context = LocalContext.current
    val dirty = name != prefs.firstName || wake != prefs.wakeMinute || sleep != prefs.sleepMinute
    GlassCard {
        KairoTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.profile_name_label),
            error = if (name.isBlank()) stringResource(R.string.profile_name_required) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TimeField(stringResource(R.string.profile_wake_short), formatMinuteOfDay(context, wake), { picking = ProfileTime.WAKE }, Modifier.weight(1f))
            TimeField(stringResource(R.string.profile_sleep_short), formatMinuteOfDay(context, sleep), { picking = ProfileTime.SLEEP }, Modifier.weight(1f))
        }
        if (dirty) PrimaryButton(stringResource(R.string.action_save), { onSave(name, wake, sleep) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
    }
    picking?.let { which ->
        TimePickerDialog(
            initialMinute = if (which == ProfileTime.WAKE) wake else sleep,
            onDismiss = { picking = null },
            onConfirm = {
                if (which == ProfileTime.WAKE) wake = it else sleep = it
                picking = null
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 1400)
@Composable
private fun SettingsContentPreview() {
    KairoTheme {
        SettingsContent(
            prefs = UserPrefs(firstName = "Aarav", onboardingDone = true),
            ai = AiSettings(),
            onSave = { _, _, _ -> },
            onSaveAi = {},
            alarms = { AlarmSettingsSection({}) },
            data = { DataSection({}, {}, {}) },
            about = { AboutSection("0.1.0-beta (12)") },
        )
    }
}
