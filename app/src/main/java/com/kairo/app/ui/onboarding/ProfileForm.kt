package com.kairo.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.ui.components.TimePickerDialog
import com.kairo.app.util.formatMinuteOfDay

private enum class ProfileTime { WAKE, SLEEP }

/** Stateless form: the caller owns the values so onboarding and Settings can share it. */
@Composable
fun ProfileForm(
    name: String,
    onNameChange: (String) -> Unit,
    wakeMinute: Int,
    onWakeChange: (Int) -> Unit,
    sleepMinute: Int,
    onSleepChange: (Int) -> Unit,
    showNameError: Boolean,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<ProfileTime?>(null) }
    val context = LocalContext.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.profile_name_label)) },
            isError = showNameError,
            supportingText = if (showNameError) {
                { Text(stringResource(R.string.profile_name_required)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        TimeRow(stringResource(R.string.profile_wake_label), formatMinuteOfDay(context, wakeMinute)) { picking = ProfileTime.WAKE }
        TimeRow(stringResource(R.string.profile_sleep_label), formatMinuteOfDay(context, sleepMinute)) { picking = ProfileTime.SLEEP }
    }

    picking?.let { which ->
        TimePickerDialog(
            initialMinute = if (which == ProfileTime.WAKE) wakeMinute else sleepMinute,
            onDismiss = { picking = null },
            onConfirm = {
                if (which == ProfileTime.WAKE) onWakeChange(it) else onSleepChange(it)
                picking = null
            },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}
