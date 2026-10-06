package com.kairo.app.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kairo.app.R
import com.kairo.app.service.BriefTileService
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.ListRow

/**
 * The always-working ways in: tile, widget, shortcut. On Android 13+ the system can show its own
 * "add tile" prompt; older versions only allow adding tiles by hand from the Quick Settings editor.
 */
@Composable
fun QuickAccessSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resultRes by remember { mutableStateOf<Int?>(null) }
    GlassCard(modifier) {
        ListRow(
            stringResource(R.string.settings_tile_title),
            subtitle = resultRes?.let { stringResource(it) } ?: stringResource(R.string.settings_tile_manual),
            icon = Icons.Outlined.TouchApp,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                KairoTextButton(stringResource(R.string.settings_add_tile_short), { requestTile(context) { resultRes = it } })
            }
        }
        ListRow(stringResource(R.string.settings_widget_title), subtitle = stringResource(R.string.settings_widget_how), icon = Icons.Outlined.Widgets)
        ListRow(stringResource(R.string.settings_shortcut_title), subtitle = stringResource(R.string.settings_shortcut_how), icon = Icons.Outlined.AppShortcut)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestTile(context: Context, onResult: (Int) -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, BriefTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile_orb),
        context.mainExecutor,
    ) { result ->
        onResult(
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.settings_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.settings_tile_already
                else -> R.string.settings_tile_not_added
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun QuickAccessSectionPreview() {
    KairoTheme { QuickAccessSection() }
}

