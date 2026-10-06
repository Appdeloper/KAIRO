package com.kairo.app.ui.focus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.prefs.FocusPrefsRepository
import com.kairo.app.data.prefs.FocusSettings
import com.kairo.app.service.focus.FocusDnd
import com.kairo.app.service.focus.FocusPromotion
import com.kairo.app.service.focus.PromotionStatus
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.domain.focus.FocusDurations
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusSettingsViewModel(private val prefs: FocusPrefsRepository, private val dnd: FocusDnd) : ViewModel() {
    val settings: StateFlow<FocusSettings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasDndAccess() = dnd.hasAccess()
    fun setDefaultMinutes(minutes: Int) = viewModelScope.launch { prefs.setDefaultMinutes(minutes) }

    /** Turning it off mid-session gives the user their notifications back right away. */
    fun setSilence(on: Boolean) = viewModelScope.launch {
        prefs.setSilenceDuringFocus(on)
        if (!on) dnd.restore()
    }
}

/** Settings > Focus: default length, optional DND, and the Live Update hint when it applies. */
@Composable
fun FocusSettingsSection(
    viewModel: FocusSettingsViewModel = viewModel(factory = containerFactory { FocusSettingsViewModel(it.focusPrefsRepository, it.focusEngine.dnd) }),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Both can change in system Settings while we're away, so re-read them on every resume.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val dndAccess = remember(resumes) { viewModel.hasDndAccess() }
    val promotion = remember(resumes) { FocusPromotion.status(context) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    var waitingForAccess by rememberSaveable { mutableStateOf(false) }

    // Came back from the access screen having granted it: finish what the user started.
    LaunchedEffect(waitingForAccess, dndAccess) {
        if (waitingForAccess && dndAccess) {
            waitingForAccess = false
            viewModel.setSilence(true)
        }
    }

    val s = settings ?: return
    FocusSettingsContent(
        settings = s,
        dndAccess = dndAccess,
        promotion = promotion,
        onDefaultMinutes = viewModel::setDefaultMinutes,
        onSilenceChange = { on ->
            when {
                !on -> viewModel.setSilence(false)
                dndAccess -> viewModel.setSilence(true)
                else -> explaining = true
            }
        },
        onOpenPromotionSettings = { context.openSafely(FocusPromotion.settingsIntent(context)) },
    )
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.focus_dnd_dialog_title)) },
            text = { Text(stringResource(R.string.focus_dnd_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    waitingForAccess = true
                    context.openSafely(FocusDnd.accessSettingsIntent())
                }) { Text(stringResource(R.string.focus_dnd_dialog_open)) }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun Context.openSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some ROMs drop these screens; app info always exists and links on to notifications.
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusSettingsContent(
    settings: FocusSettings,
    dndAccess: Boolean,
    promotion: PromotionStatus,
    onDefaultMinutes: (Int) -> Unit,
    onSilenceChange: (Boolean) -> Unit,
    onOpenPromotionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_focus), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.focus_default_length), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusDurations.CHIPS.forEach { option ->
                FilterChip(
                    selected = settings.defaultMinutes == option,
                    onClick = { onDefaultMinutes(option) },
                    label = { Text(stringResource(R.string.focus_minutes, option)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.focus_dnd_toggle), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (settings.silenceDuringFocus && !dndAccess) R.string.focus_dnd_access_lost else R.string.focus_dnd_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.silenceDuringFocus && dndAccess, onCheckedChange = onSilenceChange)
        }
        PromotionHint(promotion, onOpenPromotionSettings)
    }
}

/** Live Updates are a user-controllable permission on Android 16+; older phones get the normal countdown. */
@Composable
private fun PromotionHint(promotion: PromotionStatus, onOpen: () -> Unit) {
    val text = when (promotion) {
        PromotionStatus.ALLOWED -> return
        PromotionStatus.BLOCKED -> R.string.focus_promotion_blocked
        PromotionStatus.NOT_SUPPORTED -> R.string.focus_promotion_unsupported
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            if (promotion == PromotionStatus.BLOCKED) {
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.focus_promotion_open)) }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun FocusSettingsContentPreview() {
    KairoTheme {
        FocusSettingsContent(
            settings = FocusSettings(defaultMinutes = 45),
            dndAccess = false,
            promotion = PromotionStatus.BLOCKED,
            onDefaultMinutes = {},
            onSilenceChange = {},
            onOpenPromotionSettings = {},
        )
    }
}
