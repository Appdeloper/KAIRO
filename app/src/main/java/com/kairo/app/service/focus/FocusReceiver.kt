package com.kairo.app.service.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kairo.app.KairoApp
import com.kairo.app.data.local.FocusOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notification actions (Done, +10 min, Drop, swipe-away) and the focus alarms. Not exported: only
 * our own PendingIntents reach it. Everything works with the app closed; no activity is opened.
 */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return
        val engine = (context.applicationContext as KairoApp).container.focusEngine
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> engine.finish(sessionId, FocusOutcome.DONE, EndReason.DONE)
                    ACTION_EXTEND -> engine.extend(sessionId)
                    ACTION_DROP -> engine.finish(sessionId, FocusOutcome.DROPPED, reason = null)
                    ACTION_TIME_UP -> engine.onTimeUp(sessionId)
                    ACTION_TICK -> engine.onTick(sessionId)
                    ACTION_DISMISSED -> engine.onDismissed(sessionId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.kairo.app.focus.DONE"
        const val ACTION_EXTEND = "com.kairo.app.focus.EXTEND"
        const val ACTION_DROP = "com.kairo.app.focus.DROP"
        const val ACTION_TIME_UP = "com.kairo.app.focus.TIME_UP"
        const val ACTION_TICK = "com.kairo.app.focus.TICK"
        const val ACTION_DISMISSED = "com.kairo.app.focus.DISMISSED"
        private const val EXTRA_SESSION_ID = "session_id"

        fun intent(context: Context, action: String, sessionId: Long): Intent =
            Intent(context, FocusReceiver::class.java).setAction(action).putExtra(EXTRA_SESSION_ID, sessionId)
    }
}

/**
 * Reboots clear every alarm, so a running session is resumed, or closed as finished (with the
 * "next step" notification) if its end passed while the phone was off. App updates re-run the same
 * check, since an update can arrive mid-session.
 */
class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as KairoApp).container.focusEngine.reconcile()
            } finally {
                pending.finish()
            }
        }
    }
}
