package com.kairo.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kairo.app.MainActivity
import com.kairo.app.R
import com.kairo.app.ui.alarms.AlarmRingActivity
import com.kairo.app.ui.briefing.BriefingActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object AlarmNotifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_INFO = "alarm_info"
    const val RINGING_ID = 4_100
    private const val MISSED_ID_BASE = 4_200
    private const val SNOOZED_ID_BASE = 4_300

    /** The ALARM channel has no sound of its own: the ring service plays the alarm stream itself. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alarm_description)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, context.getString(R.string.channel_alarm_info), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Foreground notification while ringing: full-screen on a locked/idle phone, heads-up while in use. */
    fun ringing(context: Context, plan: AlarmPlan): Notification {
        val fullScreen = ringScreenIntent(context, plan.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setColor(context.getColor(R.color.kairo_primary)) // one brand accent across every KAIRO notification
            .setContentTitle(plan.label.ifBlank { context.getString(R.string.alarm_default_label) })
            .setContentText(context.getString(R.string.alarm_ringing_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (RingPolicy.canSnooze(plan)) {
            builder.addAction(0, context.getString(R.string.alarm_snooze_minutes, plan.snoozeMinutes), serviceIntent(context, AlarmRingService.snoozeIntent(context), 1))
        }
        builder.addAction(0, context.getString(R.string.alarm_dismiss), dismissIntent(context, plan))
        return builder.build()
    }

    /** Used only if the ring service couldn't start: the full-screen intent starts it from the visible ring screen. */
    fun postRingFallback(context: Context, alarmId: Long) {
        val pi = ringScreenIntent(context, alarmId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setColor(context.getColor(R.color.kairo_primary)) // one brand accent across every KAIRO notification
            .setContentTitle(context.getString(R.string.alarm_default_label))
            .setContentText(context.getString(R.string.alarm_ringing_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        notify(context, RINGING_ID, notification)
    }

    fun postMissed(context: Context, plan: AlarmPlan, rangAt: Instant) {
        val open = PendingIntent.getActivity(
            context, MISSED_ID_BASE, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = rangAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setColor(context.getColor(R.color.kairo_primary)) // one brand accent across every KAIRO notification
            .setContentTitle(context.getString(R.string.alarm_missed_title))
            .setContentText(context.getString(R.string.alarm_missed_text, plan.label.ifBlank { context.getString(R.string.alarm_default_label) }, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.alarm_open_kairo), open)
            .build()
        notify(context, MISSED_ID_BASE + plan.id.toInt(), notification)
    }

    fun postSnoozed(context: Context, plan: AlarmPlan) {
        val until = plan.snoozedUntilMillis ?: return
        val time = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setColor(context.getColor(R.color.kairo_primary)) // one brand accent across every KAIRO notification
            .setContentTitle(context.getString(R.string.alarm_snoozed_until, time))
            .setContentText(plan.label.ifBlank { context.getString(R.string.alarm_default_label) })
            .setOngoing(true)
            .addAction(0, context.getString(R.string.alarm_dismiss), serviceIntent(context, AlarmRingService.cancelSnoozeIntent(context, plan.id), 3))
            .build()
        notify(context, SNOOZED_ID_BASE + plan.id.toInt(), notification)
    }

    fun clearSnoozed(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(SNOOZED_ID_BASE + alarmId.toInt())
    }

    fun clearRingFallback(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(RINGING_ID)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    private fun ringScreenIntent(context: Context, alarmId: Long) = PendingIntent.getActivity(
        context,
        RINGING_ID,
        AlarmRingActivity.intent(context, alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Dismiss from the heads-up notification. If the alarm opens the briefing, the action launches
     * BriefingActivity directly (activity PendingIntents are allowed; trampolines are not), and the
     * briefing tells the ring service to stop.
     */
    private fun dismissIntent(context: Context, plan: AlarmPlan): PendingIntent = if (plan.openBriefingOnDismiss) {
        PendingIntent.getActivity(
            context, 2,
            BriefingActivity.dismissAlarmIntent(context, plan.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    } else {
        serviceIntent(context, AlarmRingService.dismissIntent(context), 2)
    }

    private fun serviceIntent(context: Context, intent: Intent, requestCode: Int) =
        PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
