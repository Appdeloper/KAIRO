package com.kairo.app.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.kairo.app.ui.briefing.BriefingActivity

/**
 * Tracks whether any KAIRO activity is on screen, and when the briefing last resumed. The shake
 * launcher uses this to choose the direct path and to confirm a launch actually happened
 * (blocked background launches fail silently, so "did it resume?" is the only reliable check).
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var startedActivities = 0

    @Volatile var briefingResumedAt: Long = 0L
        private set

    val isAppVisible: Boolean get() = startedActivities > 0

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is BriefingActivity) briefingResumedAt = SystemClock.elapsedRealtime()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
