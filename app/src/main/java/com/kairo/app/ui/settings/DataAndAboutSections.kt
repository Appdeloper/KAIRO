package com.kairo.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.ListRow

/** Load sample week, export everything, or start over (with a clear confirmation). */
@Composable
fun DataSection(onLoadSample: () -> Unit, onExport: () -> Unit, onReset: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val colors = KairoTheme.colors
    GlassCard {
        ListRow(stringResource(R.string.action_load_sample), subtitle = stringResource(R.string.settings_sample_sub), icon = Icons.Outlined.Science, onClick = onLoadSample)
        ListRow(stringResource(R.string.settings_export), subtitle = stringResource(R.string.settings_export_sub), icon = Icons.Outlined.Download, onClick = onExport)
        ListRow(
            stringResource(R.string.settings_reset),
            subtitle = stringResource(R.string.settings_reset_sub),
            icon = Icons.Outlined.DeleteForever,
            iconTint = colors.error,
            onClick = { confirming = true },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = colors.surface2,
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body), color = colors.textSecondary) },
            confirmButton = {
                KairoTextButton(stringResource(R.string.settings_reset_confirm), {
                    confirming = false
                    onReset()
                }, color = colors.error)
            },
            dismissButton = { KairoTextButton(stringResource(R.string.settings_reset_keep), { confirming = false }, color = colors.textSecondary) },
        )
    }
}

/** Shares the export as text through the system share sheet; nothing is uploaded by KAIRO. */
fun shareExport(context: android.content.Context, json: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_export_subject))
        .putExtra(Intent.EXTRA_TEXT, json)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Logo, version and build, and where to send feedback. */
@Composable
fun AboutSection(versionLabel: String, extra: @Composable () -> Unit = {}) {
    val colors = KairoTheme.colors
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.kairo_logo_mark), contentDescription = stringResource(R.string.cd_kairo_logo), modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(Spacing.md))
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.app_name), style = KairoTheme.type.titleLarge)
                Text(versionLabel, style = KairoTheme.numbers.small, color = colors.textSecondary)
            }
        }
        Text(stringResource(R.string.settings_about_body), style = KairoTheme.type.bodySmall, color = colors.textSecondary)
        extra()
        Text(stringResource(R.string.settings_fonts_license), style = KairoTheme.type.bodySmall, color = colors.textTertiary)
    }
}

/** "0.1.0-beta (12)" from the installed package. */
@Composable
fun rememberVersionLabel(): String {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun DataAndAboutPreview() {
    KairoTheme {
        androidx.compose.foundation.layout.Column {
            DataSection({}, {}, {})
            AboutSection("0.1.0-beta (12) · abc1234")
        }
    }
}
