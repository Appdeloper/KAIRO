package com.kairo.app.alarm

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager. Exact alarms are exempt from foreground-service start restrictions, so
 * the ring service can start from here even with the app swiped away and the screen off.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (id < 0) return
        try {
            ContextCompat.startForegroundService(context, AlarmRingService.ringIntent(context, id))
        } catch (e: IllegalStateException) {
            // Only possible on the inexact fallback path (no exact-alarm access). Don't stay silent:
            // a full-screen notification opens the ring screen, which starts the service while visible.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("KairoAlarm", "Ring service blocked; posting full-screen fallback", e)
                AlarmNotifications.postRingFallback(context, id)
            } else {
                throw e
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.kairo.app.alarm.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}

/** Re-arms every alarm whenever the system forgets them (reboot) or wall-clock time moves. */
class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                AlarmSync.syncAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31); only ever sent on 31+.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
