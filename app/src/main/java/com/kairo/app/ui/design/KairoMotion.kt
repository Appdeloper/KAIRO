package com.kairo.app.ui.design

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 400

    /** Material "standard" easing: quick start, gentle settle. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> short(): AnimationSpec<T> = tween(SHORT, easing = Standard)
    fun <T> medium(): AnimationSpec<T> = tween(MEDIUM, easing = Standard)
    fun <T> long(): AnimationSpec<T> = tween(LONG, easing = Standard)
    fun <T> gentleSpring(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /** Non-essential motion collapses to an instant change when the user turned animations off. */
    fun <T> orInstant(reduced: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> = if (reduced) snap() else spec
}

/** True when the system "Remove animations" setting is on (animator scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun readReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
