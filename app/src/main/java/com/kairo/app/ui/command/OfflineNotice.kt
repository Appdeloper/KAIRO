package com.kairo.app.ui.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.app.KairoApp
import com.kairo.app.R
import com.kairo.app.ai.AiSettings
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.components.Banner
import com.kairo.app.ui.design.components.BannerTone
import com.kairo.app.util.rememberIsOnline

/**
 * Tells the user they're offline, but only when it matters: cloud AI is on and set up. With AI off
 * (or not set up) everything already runs on the phone, so there's nothing to warn about.
 */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as KairoApp).container.aiSettingsRepository }
    val ai by repo.settings.collectAsStateWithLifecycle(AiSettings())
    val online = rememberIsOnline()
    if (ai.isReady && !online) OfflineNoticeContent(modifier)
}

@Composable
fun OfflineNoticeContent(modifier: Modifier = Modifier) {
    Banner(
        tone = BannerTone.INFO,
        title = stringResource(R.string.offline_title),
        body = stringResource(R.string.offline_body),
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun OfflineNoticePreview() {
    KairoTheme { OfflineNoticeContent() }
}
