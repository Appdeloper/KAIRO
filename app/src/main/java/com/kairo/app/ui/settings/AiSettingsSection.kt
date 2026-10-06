package com.kairo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.ai.AiSettings
import com.kairo.app.ui.design.KairoTheme

/** Stateless: the caller owns the saved value; edits stay local until Save. */
@Composable
fun AiSettingsSection(saved: AiSettings, onSave: (AiSettings) -> Unit, modifier: Modifier = Modifier) {
    var enabled by rememberSaveable(saved) { mutableStateOf(saved.enabled) }
    var url by rememberSaveable(saved) { mutableStateOf(saved.baseUrl) }
    var token by rememberSaveable(saved) { mutableStateOf(saved.deviceToken) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val draft = AiSettings(enabled, url.trim(), token.trim())
    val urlError = url.isNotBlank() && !draft.urlIsValid
    val dirty = draft != saved

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_ai), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        AiStatusCard(draft)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_ai_toggle), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.settings_ai_toggle_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.settings_ai_url)) },
            placeholder = { Text(stringResource(R.string.settings_ai_url_placeholder)) },
            isError = urlError,
            supportingText = if (urlError) {
                { Text(stringResource(R.string.settings_ai_url_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.settings_ai_token)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (showToken) R.string.cd_hide_token else R.string.cd_show_token),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(draft) }, enabled = dirty && !urlError, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (dirty) R.string.action_save else R.string.settings_saved))
        }
    }
}

@Composable
private fun AiStatusCard(settings: AiSettings) {
    val (text, color) = when {
        !settings.enabled -> stringResource(R.string.settings_ai_status_off) to MaterialTheme.colorScheme.onSurfaceVariant
        !settings.isSetUp -> stringResource(R.string.settings_ai_status_not_set_up) to KairoTheme.colors.warning
        else -> stringResource(R.string.settings_ai_status_ready) to KairoTheme.colors.success
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AiSettingsNotSetUpPreview() {
    KairoTheme { AiSettingsSection(saved = AiSettings(), onSave = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AiSettingsReadyPreview() {
    KairoTheme {
        AiSettingsSection(saved = AiSettings(true, "https://kairo-proxy.example.workers.dev", "token-1234567890"), onSave = {})
    }
}
