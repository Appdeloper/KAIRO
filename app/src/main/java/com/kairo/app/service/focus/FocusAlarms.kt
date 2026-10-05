package com.kairo.app.service.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * The focus end alarm and the progress tick. Deliberately separate from alarm/AlarmScheduler:
 * this is never an alarm-clock alarm, so no status-bar alarm icon and no alarm channel.
 * ELAPSED_REALTIME clocks so a clock change mid-session doesn't move the end.
 */
class FocusAlarms(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Exact-and-allow-while-idle: fires in Doze, but Android may hold it a few minutes in deep idle.
     * Without exact-alarm access (API 31-32 only, USE_EXACT_ALARM covers 33+) we fall back to an
     * inexact while-idle alarm (rule 7); the countdown on screen is drawn by the system either way.
     */
    fun scheduleEnd(sessionId: Long, atElapsedMillis: Long) {
        val pi = pending(FocusReceiver.ACTION_TIME_UP, sessionId, END_CODE)
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsedMillis, pi)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "exact focus alarm refused; using inexact", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsedMillis, pi)
    }

    /** Non-wakeup and inexact: refreshes the progress bar only when the phone is awake anyway. */
    fun scheduleTick(sessionId: Long, atElapsedMillis: Long) {
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, atElapsedMillis, pending(FocusReceiver.ACTION_TICK, sessionId, TICK_CODE))
    }

    fun cancelAll() {
        alarmManager.cancel(pending(FocusReceiver.ACTION_TIME_UP, 0, END_CODE))
        alarmManager.cancel(pending(FocusReceiver.ACTION_TICK, 0, TICK_CODE))
    }

    // Extras don't affect PendingIntent identity, so cancel() matches whatever session was scheduled.
    private fun pending(action: String, sessionId: Long, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        FocusReceiver.intent(context, action, sessionId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "KairoFocus"
        const val END_CODE = 7_001
        const val TICK_CODE = 7_002
    }
}

