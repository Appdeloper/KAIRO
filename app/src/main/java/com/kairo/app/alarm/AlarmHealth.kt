package com.kairo.app.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.time.Instant

enum class HealthIssue { EXACT_ALARMS, FULL_SCREEN, NOTIFICATIONS, BATTERY, ALARM_VOLUME }

/**
 * Everything that can stop an alarm from ringing on time. Missing permissions are shown as a
 * persistent banner (never a silent failure); battery and volume are advisory, on the health card.
 */
data class AlarmHealth(
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val alarmVolumeAudible: Boolean,
    val nextAlarm: Instant?,
) {
    /** Blocking problems: each one can mean an alarm that doesn't ring, or rings without its screen. */
    val missingPermissions: List<HealthIssue>
        get() = buildList {
            if (!exactAlarms) add(HealthIssue.EXACT_ALARMS)
            if (!notifications) add(HealthIssue.NOTIFICATIONS)
            if (!fullScreen) add(HealthIssue.FULL_SCREEN)
        }

    val advisories: List<HealthIssue>
        get() = buildList {
            if (!batteryUnrestricted) add(HealthIssue.BATTERY)
            if (!alarmVolumeAudible) add(HealthIssue.ALARM_VOLUME)
        }

    companion object {
        val ALL_GOOD = AlarmHealth(true, true, true, true, true, null)
    }
}

object AlarmHealthChecker {
    fun check(context: Context, nextAlarm: Instant?): AlarmHealth {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        return AlarmHealth(
            exactAlarms = AlarmScheduler(context).canScheduleExact(),
            // API 34+: only calling/alarm apps get this by default, and the user can turn it off.
            fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent(),
            notifications = notifications.areNotificationsEnabled(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            alarmVolumeAudible = audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0,
            nextAlarm = nextAlarm,
        )
    }

    /** The exact system screen that fixes [issue], deep-linked to this app where Android allows. */
    fun fixIntent(context: Context, issue: HealthIssue): Intent {
        val pkg = "package:${context.packageName}".toUri()
        val intent = when (issue) {
            HealthIssue.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else appDetails(pkg)
            HealthIssue.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else appDetails(pkg)
            HealthIssue.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            // The list screen needs no extra permission; asking per-app needs a Play-restricted one.
            HealthIssue.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            HealthIssue.ALARM_VOLUME -> Intent(Settings.ACTION_SOUND_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appDetails(pkg: Uri) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
}
