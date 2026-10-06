package com.kairo.app.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing

/** Friendly empty screen: a small static orb, one line of title, one of help, one action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxl, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OrbGlyph(size = 88.dp)
        Text(title, style = KairoTheme.type.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = KairoTheme.type.bodyMedium, color = KairoTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(Spacing.xs))
            PrimaryButton(actionLabel, onAction)
        }
        if (secondaryLabel != null && onSecondary != null) KairoTextButton(secondaryLabel, onSecondary)
    }
}

/** One permission, explained in one plain sentence, with Allow and Later. Shows "Allowed" once granted. */
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    reason: String,
    granted: Boolean,
    allowLabel: String,
    allowedLabel: String,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    laterLabel: String? = null,
    onLater: (() -> Unit)? = null,
) {
    val colors = KairoTheme.colors
    GlassCard(modifier, level = GlassLevel.ONE, elevation = if (granted) Elevation.LOW else Elevation.RAISED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (granted) colors.success else colors.primary)
            Spacer(Modifier.width(Spacing.md))
            Text(title, style = KairoTheme.type.titleMedium, modifier = Modifier.weight(1f))
            if (granted) StatusPill(allowedLabel, tone = Tone.SUCCESS, icon = Icons.Outlined.CheckCircle)
        }
        Text(reason, style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
        if (!granted) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (laterLabel != null && onLater != null) KairoTextButton(laterLabel, onLater, color = colors.textSecondary)
                Spacer(Modifier.width(Spacing.sm))
                PrimaryButton(allowLabel, onAllow)
            }
        }
    }
}

enum class BannerTone { INFO, WARNING, ERROR }

/** Inline message at the top of a screen. Icon + text, so meaning never depends on colour alone. */
@Composable
fun Banner(
    tone: BannerTone,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String? = null,
) {
    val colors = KairoTheme.colors
    val (accent, icon) = when (tone) {
        BannerTone.INFO -> colors.primary to Icons.Outlined.Info
        BannerTone.WARNING -> colors.warning to Icons.Outlined.WarningAmber
        BannerTone.ERROR -> colors.error to Icons.Outlined.ErrorOutline
    }
    GlassCard(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        fill = colors.tint(accent, 0.12f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).padding(end = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = KairoTheme.type.titleSmall)
                if (body != null) Text(body, style = KairoTheme.type.bodySmall, color = colors.textSecondary)
                if (actionLabel != null && onAction != null) {
                    KairoTextButton(actionLabel, onAction, color = accent, modifier = Modifier.padding(start = 0.dp))
                }
            }
            if (onDismiss != null) KairoIconButton(Icons.Outlined.Close, dismissLabel ?: "", onDismiss)
        }
    }
}

/** Snackbar host styled as a raised glass pill; actions use the primary colour. */
@Composable
fun KairoSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> KairoSnackbar(data) }
}

@Composable
fun KairoSnackbar(data: SnackbarData) {
    val colors = KairoTheme.colors
    Snackbar(
        snackbarData = data,
        shape = Radius.medium,
        containerColor = colors.surface3,
        contentColor = colors.textPrimary,
        actionColor = colors.primary,
        actionContentColor = colors.primary,
        dismissActionContentColor = colors.textSecondary,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 900)
@Composable
private fun FeedbackPreview() {
    KairoTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Banner(BannerTone.WARNING, "Alarms may ring late", body = "Allow exact alarms so they ring on time.", actionLabel = "Fix it", onAction = {})
            Banner(BannerTone.ERROR, "Couldn't reach your AI", body = "Using the offline planner for now.")
            Banner(BannerTone.INFO, "Offline brief", onDismiss = {}, dismissLabel = "Dismiss")
            PermissionCard(Icons.Outlined.Mic, "Microphone", "So you can talk to KAIRO. Only while the briefing is open.", granted = false, allowLabel = "Allow", allowedLabel = "Allowed", onAllow = {}, laterLabel = "Later", onLater = {})
            EmptyState("Nothing planned yet", "Add your timetable or load a sample week to explore.", actionLabel = "Add your timetable", onAction = {}, secondaryLabel = "Load sample week", onSecondary = {})
        }
    }
}
