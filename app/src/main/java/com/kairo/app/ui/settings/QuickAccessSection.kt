package com.kairo.app.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.service.BriefTileService
import com.kairo.app.ui.theme.KairoTheme

/**
 * Helps the user install the fallback entry points. On Android 13+ the system can show its own
 * "add tile" prompt; older versions only allow adding tiles by hand from the QS editor.
 */
@Composable
fun QuickAccessSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resultRes by remember { mutableStateOf<Int?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_entry_points), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { requestTile(context) { resultRes = it } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_tile))
            }
            resultRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(stringResource(R.string.settings_tile_manual), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun QuickAccessSectionPreview() {
    KairoTheme { QuickAccessSection() }
}
