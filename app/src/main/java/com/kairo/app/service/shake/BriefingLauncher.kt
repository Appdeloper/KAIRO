package com.kairo.app.service.shake

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.kairo.app.R
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.util.AppVisibility

/**
 * Opens the briefing after a shake using the strongest path Android allows right now:
 *  1. direct    - a KAIRO screen is already visible (visible-window exemption);
 *  2. overlay   - "Display over other apps" granted: show a small orb overlay, wait until it is
 *                 actually visible (Android 15 ignores non-visible windows), then launch;
 *  3. notification - heads-up "Tap to open your briefing" (the user's tap is always allowed).
 * Blocked launches fail silently, so every launch is verified by checking that the briefing resumed.
 */
class BriefingLauncher(private val context: Context, private val onPath: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    fun launch() {
        val requestedAt = SystemClock.elapsedRealtime()
        when {
            AppVisibility.isAppVisible -> {
                startBriefing()
                verify(requestedAt, PATH_DIRECT)
            }
            Settings.canDrawOverlays(context) -> launchViaOverlay(requestedAt)
            else -> fallback(PATH_NOTIFICATION)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun launchViaOverlay(requestedAt: Long) {
        val windowManager = context.getSystemService(WindowManager::class.java)
        var launched = false
        val orb = object : ImageView(context) {
            override fun onWindowVisibilityChanged(visibility: Int) {
                super.onWindowVisibilityChanged(visibility)
                if (visibility == View.VISIBLE && !launched) {
                    launched = true
                    // Next frame: the overlay has been drawn, so the system sees a visible window.
                    post {
                        startBriefing()
                        verify(requestedAt, PATH_OVERLAY)
                    }
                }
            }
        }.apply { setImageResource(R.drawable.widget_orb) }

        val size = (ORB_DP * context.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        try {
            windowManager.addView(orb, params)
            overlay = orb
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException if the permission was just revoked.
            Log.w(TAG, "Overlay refused", e)
            fallback(PATH_OVERLAY_FAILED)
            return
        }
        main.postDelayed({
            if (!launched) {
                removeOverlay()
                fallback(PATH_OVERLAY_NEVER_VISIBLE)
            }
        }, OVERLAY_VISIBLE_TIMEOUT_MS)
    }

    private fun startBriefing() {
        context.startActivity(BriefingActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun verify(requestedAt: Long, path: String) {
        main.postDelayed({
            removeOverlay()
            if (AppVisibility.briefingResumedAt >= requestedAt) record(path) else fallback("$path$BLOCKED_SUFFIX")
        }, VERIFY_MS)
    }

    private fun fallback(reason: String) {
        val shown = ShakeNotifications.postOpenBriefing(context)
        record(if (shown) "$PATH_NOTIFICATION($reason)" else "$PATH_NONE($reason)")
    }

    private fun record(path: String) {
        Log.i(TAG, "shake launch path=$path")
        onPath(path)
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            runCatching { context.getSystemService(WindowManager::class.java).removeView(view) }
        }
        overlay = null
    }

    companion object {
        const val TAG = "KairoShake"
        const val PATH_DIRECT = "direct"
        const val PATH_OVERLAY = "overlay"
        const val PATH_NOTIFICATION = "notification"
        const val PATH_NONE = "none-notifications-off"
        private const val PATH_OVERLAY_FAILED = "overlay-refused"
        private const val PATH_OVERLAY_NEVER_VISIBLE = "overlay-not-visible"
        private const val BLOCKED_SUFFIX = "-blocked"
        private const val ORB_DP = 96
        private const val VERIFY_MS = 1_500L
        private const val OVERLAY_VISIBLE_TIMEOUT_MS = 1_000L
    }
}
