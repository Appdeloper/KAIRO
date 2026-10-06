package com.kairo.app.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The only haptics the app uses: a light tick when something is applied, a firmer confirm when
 * something is completed. Nothing fires on scroll. Respects the system haptics setting because it
 * goes through View.performHapticFeedback.
 */
class KairoHaptics(private val view: View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun success() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberKairoHaptics(): KairoHaptics {
    val view = LocalView.current
    return remember(view) { KairoHaptics(view) }
}
