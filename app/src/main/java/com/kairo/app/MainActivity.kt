package com.kairo.app

import android.os.Bundle
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
        setContent {
            KairoTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    KairoRoot()
                }
            }
        }
    }
}
