package com.kairo.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kairo.app.ui.design.KairoTheme
import org.robolectric.RuntimeEnvironment

/**
 * Renders one composable and saves it under build/outputs/roborazzi/<name>.png. Only writes files
 * when run through `./gradlew :app:recordRoborazziDebug`; a plain test run just renders (which
 * still catches crashes in screen code).
 *
 * The clock is paused so infinite animations (the orb) can't keep Compose busy forever.
 */
object Shots {
    const val DIR = "build/outputs/roborazzi"

    fun capture(
        rule: ComposeContentTestRule,
        name: String,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KairoTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(ANIMATION_SETTLE_MS)
        rule.onRoot().captureRoboImage("$DIR/$name.png")
    }

    private const val ANIMATION_SETTLE_MS = 1_200L
}
