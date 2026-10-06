package com.kairo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.ai.AiSettings
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.ToggleRow
import com.kairo.app.ui.design.components.Tone

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

    GlassCard(modifier) {
        AiStatus(draft)
        ToggleRow(stringResource(R.string.settings_ai_toggle), enabled, { enabled = it }, subtitle = stringResource(R.string.settings_ai_toggle_hint))
        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                KairoTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.settings_ai_url),
                    placeholder = stringResource(R.string.settings_ai_url_placeholder),
                    error = if (urlError) stringResource(R.string.settings_ai_url_error) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                AiTokenField(token, { token = it }, showToken, { showToken = !showToken })
            }
        }
        if (dirty) {
            PrimaryButton(stringResource(R.string.action_save), { onSave(draft) }, Modifier.fillMaxWidth().padding(top = Spacing.xs), enabled = !urlError)
        }
    }
}

@Composable
private fun AiTokenField(token: String, onToken: (String) -> Unit, showToken: Boolean, onToggleShow: () -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = token,
        onValueChange = onToken,
        label = { Text(stringResource(R.string.settings_ai_token)) },
        singleLine = true,
        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            KairoIconButton(
                if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                stringResource(if (showToken) R.string.cd_hide_token else R.string.cd_show_token),
                onToggleShow,
            )
        },
        shape = com.kairo.app.ui.design.Radius.medium,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = KairoTheme.colors.surface3,
            unfocusedContainerColor = KairoTheme.colors.surface3,
            focusedBorderColor = KairoTheme.colors.primary,
            unfocusedBorderColor = KairoTheme.colors.outline,
            focusedLabelColor = KairoTheme.colors.primary,
            unfocusedLabelColor = KairoTheme.colors.textSecondary,
            cursorColor = KairoTheme.colors.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AiStatus(settings: AiSettings) {
    when {
        !settings.enabled -> StatusPill(stringResource(R.string.settings_ai_status_off_short), icon = Icons.Outlined.CloudOff)
        !settings.isSetUp -> StatusPill(stringResource(R.string.settings_ai_status_not_set_up_short), tone = Tone.WARNING, icon = Icons.Outlined.WarningAmber)
        else -> StatusPill(stringResource(R.string.settings_ai_status_ready_short), tone = Tone.SUCCESS, icon = Icons.Outlined.CheckCircle)
    }
    Text(
        stringResource(
            when {
                !settings.enabled -> R.string.settings_ai_status_off
                !settings.isSetUp -> R.string.settings_ai_status_not_set_up
                else -> R.string.settings_ai_status_ready
            },
        ),
        style = KairoTheme.type.bodyMedium,
        color = KairoTheme.colors.textSecondary,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun AiSettingsNotSetUpPreview() {
    KairoTheme { AiSettingsSection(saved = AiSettings(enabled = true), onSave = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun AiSettingsReadyPreview() {
    KairoTheme {
        AiSettingsSection(saved = AiSettings(true, "https://kairo-proxy.example.workers.dev", "token-1234567890"), onSave = {})
    }
}
