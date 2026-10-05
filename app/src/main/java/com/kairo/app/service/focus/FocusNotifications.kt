package com.kairo.app.service.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.domain.focus.ClockReading
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.ui.focus.FocusActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Why a session ended, which picks the wording of the "next step" notification. */
enum class EndReason { TIME_UP, DONE, RECOVERED }

object FocusNotifications {
    const val CHANNEL_FOCUS = "focus"
    const val CHANNEL_FOCUS_END = "focus_end"
    private const val ONGOING_ID = 5_100
    private const val END_ID = 5_200
    private const val PROGRESS_SCALE = 1_000

    /**
     * "focus" is silent and doesn't vibrate, but DEFAULT importance (not LOW/MIN) so the countdown
     * shows on the lock screen and stays eligible for promotion (MIN channels can't be promoted).
     * "focus_end" is HIGH so "Time's up" peeks in as a heads-up.
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_focus_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS_END, context.getString(R.string.channel_focus_end), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_focus_end_description)
            },
        )
    }

    /**
     * The running session. Android 16+: asks for promotion (Live Update) with ProgressStyle. Older:
     * a normal ongoing notification. Both count down with the system chronometer, so the time stays
     * right with the screen off and our process dead; we only re-post for Extend and progress ticks.
     */
    fun showOngoing(context: Context, session: FocusSession, roleColor: Int, now: ClockReading) {
        val endWall = FocusTiming.endWallMillis(session, now)
        val builder = baseOngoing(context, session, endWall, roleColor)
            .setContentTitle(session.blockLabel)
            .setContentText(context.getString(R.string.focus_notif_text, formatTime(endWall)))
            .setContentIntent(openSession(context, session.id))
            .setDeleteIntent(broadcast(context, FocusReceiver.ACTION_DISMISSED, session.id, 4))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, session, endWall, roleColor))
            .addAction(0, context.getString(R.string.focus_action_done), broadcast(context, FocusReceiver.ACTION_DONE, session.id, 1))
            .addAction(0, context.getString(R.string.focus_action_extend), broadcast(context, FocusReceiver.ACTION_EXTEND, session.id, 2))
            .addAction(0, context.getString(R.string.focus_action_drop), broadcast(context, FocusReceiver.ACTION_DROP, session.id, 3))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Promotion is requested here and nowhere else: only an active, user-started focus session.
            builder.setRequestPromotedOngoing(true).setStyle(progressStyle(context, session, roleColor, now))
        } else {
            builder.setProgress(PROGRESS_SCALE, (FocusTiming.progress(session, now) * PROGRESS_SCALE).toInt(), false)
        }
        notify(context, ONGOING_ID, builder.build())
    }

    fun cancelOngoing(context: Context) = manager(context).cancel(ONGOING_ID)

    /** High-priority heads-up with "Write next step". Never a full-screen intent, never an activity launch. */
    fun postEnded(context: Context, session: FocusSession, reason: EndReason) {
        val title = when (reason) {
            EndReason.TIME_UP -> context.getString(R.string.focus_end_title_time_up, session.blockLabel)
            EndReason.DONE -> context.getString(R.string.focus_end_title_done, session.blockLabel)
            EndReason.RECOVERED -> context.getString(R.string.focus_end_title_recovered, session.blockLabel)
        }
        val write = PendingIntent.getActivity(
            context, END_ID,
            FocusActivity.nextStepIntent(context, session.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS_END)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.focus_end_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(write)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.focus_action_write_next_step), write)
            .build()
        notify(context, END_ID, notification)
    }

    fun cancelEnded(context: Context) = manager(context).cancel(END_ID)

    private fun baseOngoing(context: Context, session: FocusSession, endWall: Long, roleColor: Int) =
        NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_tile_orb)
            .setColor(roleColor) // accent only; colorized notifications can't be promoted
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(endWall)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openSession(context, session.id))

    /** What the lock screen shows when the user hides sensitive content: no task title. */
    private fun publicVersion(context: Context, session: FocusSession, endWall: Long, roleColor: Int): Notification =
        baseOngoing(context, session, endWall, roleColor)
            .setContentTitle(context.getString(R.string.focus_notif_public_title))
            .setContentText(context.getString(R.string.focus_notif_text, formatTime(endWall)))
            .build()

    /** One segment for the planned time in the role colour, a second for extensions. Units: seconds. */
    private fun progressStyle(context: Context, session: FocusSession, roleColor: Int, now: ClockReading): NotificationCompat.ProgressStyle {
        val totalSeconds = (FocusTiming.plannedDurationMillis(session) / 1_000).toInt().coerceAtLeast(1)
        val plannedSeconds = (session.plannedMinutes * 60).coerceAtMost(totalSeconds)
        val extraSeconds = totalSeconds - plannedSeconds
        val segments = buildList {
            add(NotificationCompat.ProgressStyle.Segment(plannedSeconds).setColor(roleColor))
            if (extraSeconds > 0) add(NotificationCompat.ProgressStyle.Segment(extraSeconds).setColor(context.getColor(R.color.focus_extension)))
        }
        return NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(segments)
            .setProgress((FocusTiming.progress(session, now) * totalSeconds).toInt())
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_tile_orb))
    }

    private fun openSession(context: Context, sessionId: Long) = PendingIntent.getActivity(
        context, ONGOING_ID,
        FocusActivity.sessionIntent(context, sessionId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, sessionId: Long, code: Int) = PendingIntent.getBroadcast(
        context, ONGOING_ID + code,
        FocusReceiver.intent(context, action, sessionId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun formatTime(wallMillis: Long): String =
        Instant.ofEpochMilli(wallMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun notify(context: Context, id: Int, notification: Notification) {
        val m = manager(context)
        if (m.areNotificationsEnabled()) m.notify(id, notification)
    }
}

/** Live Update eligibility the user controls (Android 16+). */
enum class PromotionStatus { NOT_SUPPORTED, ALLOWED, BLOCKED }

object FocusPromotion {
    fun status(context: Context): PromotionStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA -> PromotionStatus.NOT_SUPPORTED
        context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications() -> PromotionStatus.ALLOWED
        else -> PromotionStatus.BLOCKED
    }

    /** The SDK 37 constant (the Live Update guide's ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS isn't in the SDK). */
    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}
