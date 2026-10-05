package com.kairo.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.ui.alarms.AlarmsViewModel
import com.kairo.app.ui.alarms.alarmsViewModelFactory
import com.kairo.app.util.formatMinuteOfDay
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.theme.KairoTheme

@Composable
fun OnboardingScreen(
    viewModel: ProfileViewModel = viewModel(factory = containerFactory { ProfileViewModel(it.userPrefsRepository, it.roleRepository) }),
    alarmsViewModel: AlarmsViewModel = viewModel(factory = alarmsViewModelFactory()),
) {
    val wakeLabel = stringResource(R.string.alarm_wake_label)
    // Navigation away happens reactively once DataStore reports onboardingDone = true.
    OnboardingContent(onFinish = { name, wake, sleep, addWakeAlarm ->
        // The alarm is created only because the user ticked the box; never by default.
        if (addWakeAlarm) alarmsViewModel.createWeekdayWakeAlarm(wake, wakeLabel)
        viewModel.save(name, wake, sleep)
    })
}

@Composable
fun OnboardingContent(onFinish: (name: String, wakeMinute: Int, sleepMinute: Int, addWakeAlarm: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var addWakeAlarm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var wake by rememberSaveable { mutableIntStateOf(UserPrefs.DEFAULT_WAKE_MINUTE) }
    var sleep by rememberSaveable { mutableIntStateOf(UserPrefs.DEFAULT_SLEEP_MINUTE) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 48.dp),
        )
        Text(stringResource(R.string.onboarding_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileForm(
            name = name,
            onNameChange = { name = it },
            wakeMinute = wake,
            onWakeChange = { wake = it },
            sleepMinute = sleep,
            onSleepChange = { sleep = it },
            showNameError = showErrors && name.isBlank(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = addWakeAlarm, onCheckedChange = { addWakeAlarm = it })
            Text(stringResource(R.string.onboarding_wake_alarm, formatMinuteOfDay(context, wake)), style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { if (name.isBlank()) showErrors = true else onFinish(name, wake, sleep, addWakeAlarm) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_start)) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OnboardingContentPreview() {
    KairoTheme { OnboardingContent(onFinish = { _, _, _, _ -> }) }
}
