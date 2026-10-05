package com.kairo.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.kairo.app.KairoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Foreground service (type mediaPlayback) that rings one alarm: sound, vibration, wake lock and
 * the full-screen notification. Started by AlarmReceiver; stopped by snooze, dismiss or the
 * 10-minute auto-silence.
 */
class AlarmRingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private lateinit var vibrator: AlarmVibrator
    private var wakeLock: PowerManager.WakeLock? = null

    /** Writes still in flight; the service must not stop (and cancel them) until they finish. */
    private var pendingWrites = 0

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
        vibrator = AlarmVibrator(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1))
            ACTION_SNOOZE -> RingState.current.value?.let { snooze(it.plan) }
            ACTION_DISMISS -> RingState.current.value?.let { finishRinging(it.plan, missed = false) }
            ACTION_CANCEL_SNOOZE -> cancelSnooze(intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1))
        }
        if (RingState.current.value == null) stopIfIdle()
        return START_NOT_STICKY
    }

    private fun ring(alarmId: Long) {
        val plan = AlarmMirror(this).find(alarmId)
        if (plan == null) {
            // Alarm deleted after it was scheduled. startForegroundService() still obliges us to call
            // startForeground(), or Android kills the app; do it, then stop quietly.
            val placeholder = AlarmPlan(alarmId, "", com.kairo.app.data.local.AlarmType.ONE_SHOT, 0, AlarmDays.ONE_SHOT, enabled = false)
            ServiceCompat.startForeground(this, AlarmNotifications.RINGING_ID, AlarmNotifications.ringing(this, placeholder), foregroundType())
            return
        }
        // Must call startForeground promptly after startForegroundService().
        ServiceCompat.startForeground(this, AlarmNotifications.RINGING_ID, AlarmNotifications.ringing(this, plan), foregroundType())
        AlarmNotifications.clearRingFallback(this)
        AlarmNotifications.clearSnoozed(this, plan.id)

        if (RingState.current.value?.plan?.id == plan.id) return
        // A second alarm while one rings replaces it; the first is recorded as missed, not lost.
        RingState.current.value?.let { finishRinging(it.plan, missed = true) }

        acquireWakeLock()
        RingState.set(RingState.Ringing(plan, System.currentTimeMillis()))
        player.start(plan)
        if (plan.vibrate) vibrator.start()
        main.removeCallbacks(autoSilence)
        main.postDelayed(autoSilence, AUTO_SILENCE_MS)
        // Arm the next occurrence right away; ring-time decisions only touch the snooze slot.
        AlarmSync.scheduleAfterFire(this, plan)
    }

    private val autoSilence = Runnable {
        RingState.current.value?.let { finishRinging(it.plan, missed = true) }
    }

    private fun snooze(plan: AlarmPlan) {
        val snoozed = RingPolicy.afterSnooze(plan, Instant.now()) ?: return
        AlarmScheduler(this).schedule(plan.id, AlarmScheduler.Slot.SNOOZE, Instant.ofEpochMilli(snoozed.snoozedUntilMillis!!))
        AlarmNotifications.postSnoozed(this, snoozed)
        endRinging(snoozed)
    }

    /** Dismiss and auto-silence end the same way; a missed alarm also leaves a notification. */
    private fun finishRinging(plan: AlarmPlan, missed: Boolean) {
        val done = RingPolicy.afterDismiss(plan, ZonedDateTime.now())
        AlarmScheduler(this).cancel(plan.id, AlarmScheduler.Slot.SNOOZE)
        if (missed) AlarmNotifications.postMissed(this, plan, Instant.ofEpochMilli(RingState.current.value?.startedAtMillis ?: System.currentTimeMillis()))
        endRinging(done)
    }

    private fun cancelSnooze(alarmId: Long) {
        val plan = AlarmMirror(this).find(alarmId) ?: return
        AlarmScheduler(this).cancel(alarmId, AlarmScheduler.Slot.SNOOZE)
        AlarmNotifications.clearSnoozed(this, alarmId)
        persist(RingPolicy.afterDismiss(plan, ZonedDateTime.now()))
    }

    private fun endRinging(updated: AlarmPlan) {
        stopOutputs()
        RingState.set(null)
        persist(updated)
    }

    /** Mirror first (works before unlock), then Room when available; Room's change re-syncs AlarmManager. */
    private fun persist(plan: AlarmPlan) {
        AlarmMirror(this).upsert(plan)
        pendingWrites++
        scope.launch {
            try {
                if (isUserUnlocked()) withContext(Dispatchers.IO) { (application as KairoApp).container.alarmRepository.applyRuntime(plan) }
                withContext(Dispatchers.IO) { AlarmSync.syncAll(this@AlarmRingService) }
            } finally {
                pendingWrites--
                stopIfIdle()
            }
        }
    }

    private fun stopOutputs() {
        main.removeCallbacks(autoSilence)
        player.stop()
        vibrator.stop()
        releaseWakeLock()
    }

    private fun stopIfIdle() {
        if (RingState.current.value != null || pendingWrites > 0) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(AUTO_SILENCE_MS + WAKE_LOCK_MARGIN_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopOutputs()
        RingState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    companion object {
        private const val ACTION_RING = "com.kairo.app.alarm.RING"
        private const val ACTION_SNOOZE = "com.kairo.app.alarm.SNOOZE"
        private const val ACTION_DISMISS = "com.kairo.app.alarm.DISMISS"
        private const val ACTION_CANCEL_SNOOZE = "com.kairo.app.alarm.CANCEL_SNOOZE"
        const val AUTO_SILENCE_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_MARGIN_MS = 60 * 1000L
        private const val WAKE_LOCK_TAG = "kairo:alarm"

        fun ringIntent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmRingService::class.java).setAction(ACTION_RING).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)

        fun snoozeIntent(context: Context): Intent = Intent(context, AlarmRingService::class.java).setAction(ACTION_SNOOZE)
        fun dismissIntent(context: Context): Intent = Intent(context, AlarmRingService::class.java).setAction(ACTION_DISMISS)
        fun cancelSnoozeIntent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmRingService::class.java).setAction(ACTION_CANCEL_SNOOZE).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
    }
}
