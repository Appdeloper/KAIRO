package com.kairo.app.ui.shake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.app.KairoApp
import com.kairo.app.R
import com.kairo.app.data.prefs.ShakeSettings
import com.kairo.app.service.shake.ShakeControl
import com.kairo.app.service.shake.ShakeHealthPolicy
import com.kairo.app.service.shake.ShakeService
import com.kairo.app.service.shake.ShakeStatus
import com.kairo.app.ui.theme.KairoTheme
import kotlinx.coroutines.launch

/** Shake health recomputed on every resume, from the heartbeat and whether the service is alive here. */
@Composable
fun rememberShakeStatus(): Pair<ShakeStatus, ShakeSettings> {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as KairoApp).container.shakePrefsRepository }
    val settings by prefs.settings.collectAsStateWithLifecycle(ShakeSettings())
    val running by ShakeService.running.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val status = remember(settings, running, resumes) {
        ShakeHealthPolicy.status(settings.enabled, running, settings.lastHeartbeatMillis, System.currentTimeMillis())
    }
    return status to settings
}

/**
 * Shown on Today and on the briefing (so tile and widget opens see it too). If shake is on but the
 * service isn't running and its heartbeat is still fresh, it is re-armed silently; if the heartbeat
 * is stale, the user is told the truth and re-arms it with one tap (from this visible screen).
 */
@Composable
fun ShakeStoppedCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (status, settings) = rememberShakeStatus()
    val running by ShakeService.running.collectAsStateWithLifecycle()
    LaunchedEffect(status, running, settings.enabled) {
        if (settings.enabled && !running && status == ShakeStatus.ARMED) ShakeControl.start(context)
    }
    if (status != ShakeStatus.STOPPED) return
    ShakeStoppedCardContent(onFix = { scope.launch { ShakeControl.restart(context) } }, modifier = modifier)
}

@Composable
fun ShakeStoppedCardContent(onFix: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onFix),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.shake_stopped_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.shake_stopped_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun ShakeStoppedCardPreview() {
    KairoTheme { ShakeStoppedCardContent(onFix = {}) }
}
