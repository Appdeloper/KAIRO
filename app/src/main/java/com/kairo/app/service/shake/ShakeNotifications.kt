package com.kairo.app.service.shake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kairo.app.MainActivity
import com.kairo.app.R
import com.kairo.app.ui.briefing.BriefingActivity

object ShakeNotifications {
    private const val CHANNEL_STATUS = "shake_status"
    private const val CHANNEL_OPEN = "shake_open"
    const val STATUS_ID = 5_100
    private const val OPEN_ID = 5_101
    private const val REARM_ID = 5_102
    private const val OPEN_TIMEOUT_MS = 30_000L

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_shake_status), NotificationManager.IMPORTANCE_LOW),
        )
        // HIGH importance so the fallback shows as a heads-up banner the user can tap.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_OPEN, context.getString(R.string.channel_shake_open), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** The persistent "shake is on" notification every foreground service must show, with Stop. */
    fun status(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
        .setSmallIcon(R.drawable.ic_tile_orb)
        .setContentTitle(context.getString(R.string.shake_status_title))
        .setContentText(context.getString(R.string.shake_status_text))
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(activity(context, 0, Intent(context, MainActivity::class.java)))
        .addAction(
            0, context.getString(R.string.shake_stop),
            PendingIntent.getService(context, 1, ShakeService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        .build()

    /** Fallback when Android blocks opening the briefing from the background. Not a full-screen intent. */
    fun postOpenBriefing(context: Context): Boolean = notify(
        context, OPEN_ID,
        NotificationCompat.Builder(context, CHANNEL_OPEN)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setContentTitle(context.getString(R.string.shake_open_title))
            .setContentText(context.getString(R.string.shake_open_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(OPEN_TIMEOUT_MS)
            .setContentIntent(activity(context, 2, BriefingActivity.intent(context)))
            .build(),
    )

    fun postRearm(context: Context): Boolean = notify(
        context, REARM_ID,
        NotificationCompat.Builder(context, CHANNEL_OPEN)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setContentTitle(context.getString(R.string.shake_rearm_title))
            .setContentText(context.getString(R.string.shake_rearm_text))
            .setAutoCancel(true)
            .setContentIntent(activity(context, 3, MainActivity.rearmShakeIntent(context)))
            .build(),
    )

    fun clearRearm(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(REARM_ID)

    private fun activity(context: Context, code: Int, intent: Intent) = PendingIntent.getActivity(
        context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** False when notifications are off, so the caller can log that the fallback couldn't show. */
    private fun notify(context: Context, id: Int, notification: Notification): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.notify(id, notification)
        return true
    }
}
