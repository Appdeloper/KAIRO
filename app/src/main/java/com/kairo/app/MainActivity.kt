package com.kairo.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.kairo.app.service.shake.ShakeControl
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.kairo.app.ui.navigation.KairoRoot
import com.kairo.app.ui.theme.KairoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is enforced for targetSdk 35+; opting in explicitly keeps behavior identical on API 26–34.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleRearm(intent)
        // Force-stop cancels our alarms: on every cold open, resume or close a stuck focus session.
        if (savedInstanceState == null) lifecycleScope.launch { (application as KairoApp).container.focusEngine.reconcile() }
        setContent {
            KairoTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    KairoRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRearm(intent)
    }

    /** "Tap to re-arm shake": the tap made us visible, which is what lets the service start now. */
    private fun handleRearm(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REARM_SHAKE, false) != true) return
        intent.removeExtra(EXTRA_REARM_SHAKE)
        lifecycleScope.launch { ShakeControl.restart(this@MainActivity) }
    }

    companion object {
        private const val EXTRA_REARM_SHAKE = "rearm_shake"

        fun rearmShakeIntent(context: Context): Intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_REARM_SHAKE, true)
    }
}
