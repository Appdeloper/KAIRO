package com.kairo.app.ui.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.service.focus.AndroidFocusClock
import com.kairo.app.ui.theme.KairoColors
import com.kairo.app.ui.theme.KairoTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Remaining time for a session, recomputed from the stored anchors every second while shown. */
@Composable
fun rememberFocusRemaining(session: FocusSession): Long {
    val context = LocalContext.current.applicationContext
    val clock = remember(context) { AndroidFocusClock(context) }
    var remaining by remember(session) { mutableLongStateOf(FocusTiming.remainingMillis(session, clock.read())) }
    LaunchedEffect(session) {
        while (true) {
            remaining = FocusTiming.remainingMillis(session, clock.read())
            delay(TICK_MS)
        }
    }
    return remaining
}

@Composable
fun FocusChip(label: String, remainingMillis: Long, onClick: () -> Unit, tint: Color = KairoColors.NeonCyan) {
    AssistChip(
        onClick = onClick,
        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, tint = tint) },
        label = { Text(stringResource(R.string.focus_chip, formatCountdown(remainingMillis), label)) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

/** mm:ss under an hour, h:mm:ss above. */
fun formatCountdown(millis: Long): String {
    val total = (millis + 999) / 1_000 // round up so "0:00" only shows when time is really up
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

private const val TICK_MS = 1_000L

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun FocusChipPreview() {
    KairoTheme { FocusChip("Edit reel #12", remainingMillis = 12 * 60_000L + 4_000, onClick = {}) }
}
