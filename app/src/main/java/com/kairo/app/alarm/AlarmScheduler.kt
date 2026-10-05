package com.kairo.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kairo.app.MainActivity
import java.time.Instant

/** Thin wrapper over AlarmManager. Each alarm has two independent slots: its next occurrence and a snooze. */
class AlarmScheduler(private val context: Context) {
    enum class Slot(val code: Int) { OCCURRENCE(0), SNOOZE(1) }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** API 31+ needs special access (granted automatically via USE_EXACT_ALARM on 33+). */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * setAlarmClock: exempt from Doze, never shifted by the system, and shows the status-bar alarm
     * icon. If exact alarms are denied we still set a best-effort alarm (rule 7) and the in-app
     * banner tells the user it may be late.
     */
    fun schedule(alarmId: Long, slot: Slot, at: Instant) {
        val fire = firePendingIntent(alarmId, slot)
        val millis = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showAppIntent()), fire)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused; using inexact fallback", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, fire)
    }

    fun cancel(alarmId: Long, slot: Slot) {
        alarmManager.cancel(firePendingIntent(alarmId, slot))
    }

    private fun firePendingIntent(alarmId: Long, slot: Slot): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(context, requestCode(alarmId, slot), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** What the system opens when the user taps the alarm in the status bar / lock screen. */
    private fun showAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SHOW_REQUEST_CODE,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(alarmId: Long, slot: Slot) = (alarmId * 2 + slot.code).toInt()

    private companion object {
        const val TAG = "KairoAlarm"
        const val SHOW_REQUEST_CODE = -1
    }
}
