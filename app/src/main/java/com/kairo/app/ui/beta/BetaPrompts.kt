package com.kairo.app.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.KairoBottomSheet
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.SheetFrame
import com.kairo.app.util.beta.BetaEvent
import com.kairo.app.util.beta.BetaSupport
import com.kairo.app.util.beta.Events
import com.kairo.app.util.beta.Release

/**
 * Shown once on the main screen: first a pending crash report (Share or Dismiss), otherwise the
 * "What's new" sheet the first time a new version opens. Never both at once.
 */
@Composable
fun BetaPrompts() {
    val context = LocalContext.current
    var crash by rememberSaveable { mutableStateOf(BetaSupport.crashLog.pending()) }
    var whatsNew by rememberSaveable { mutableStateOf(crash == null && BetaSupport.shouldShowWhatsNew(context, onboardingDone = true)) }
    crash?.let { report ->
        CrashPrompt(
            onShare = {
                Events.record(BetaEvent.CRASH_SHARED)
                context.startActivity(BetaSupport.crashIntent(context, report))
                BetaSupport.crashLog.clear()
                crash = null
            },
            onDismiss = {
                BetaSupport.crashLog.clear()
                crash = null
            },
        )
    }
    if (whatsNew) {
        LaunchedEffect(Unit) { Events.record(BetaEvent.WHATS_NEW_SHOWN) }
        WhatsNewSheet(onDismiss = {
            BetaSupport.markWhatsNewSeen(context)
            whatsNew = false
        })
    }
}

@Composable
fun CrashPrompt(onShare: () -> Unit, onDismiss: () -> Unit) {
    val colors = KairoTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface2,
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body), color = colors.textSecondary) },
        confirmButton = { KairoTextButton(stringResource(R.string.crash_share), onShare) },
        dismissButton = { KairoTextButton(stringResource(R.string.crash_dismiss), onDismiss, color = colors.textSecondary) },
    )
}

@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val releases = remember { BetaSupport.releases(context) }
    KairoBottomSheet(onDismissRequest = onDismiss) { WhatsNewContent(releases, onDismiss) }
}

@Composable
fun WhatsNewContent(releases: List<Release>, onDone: () -> Unit) {
    val colors = KairoTheme.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.whats_new_title), style = KairoTheme.type.headlineSmall, modifier = Modifier.semantics { heading() })
        releases.take(MAX_RELEASES).forEach { release ->
            Text(release.version, style = KairoTheme.type.labelLarge, color = colors.primary)
            release.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("•", style = KairoTheme.type.bodyMedium, color = colors.primary)
                    Text(item, style = KairoTheme.type.bodyMedium)
                }
            }
        }
        PrimaryButton(stringResource(R.string.whats_new_done), onDone, Modifier.fillMaxWidth())
    }
}

private const val MAX_RELEASES = 3

/** Shared with screenshot tests. */
object BetaPreviewData {
    val releases = listOf(
        Release("0.1.0-beta", listOf("A fresh new look with the KAIRO orb front and centre.", "Four tabs: Today, Plan, Alarms and Settings.", "Every suggested change is shown first, with Undo.")),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun WhatsNewPreview() {
    KairoTheme { SheetFrame { WhatsNewContent(BetaPreviewData.releases, {}) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun CrashPromptPreview() {
    KairoTheme { CrashPrompt({}, {}) }
}
