package com.kairo.app.ui.alarms

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kairo.app.KairoApp
import com.kairo.app.alarm.AlarmRingService
import com.kairo.app.alarm.RingPolicy
import com.kairo.app.alarm.RingState
import com.kairo.app.alarm.isUserUnlocked
import com.kairo.app.domain.brief.Upcoming
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.today.DayTimelineSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Full-screen ring UI, shown over the lock screen with the screen turned on. The service owns the
 * sound; this screen only offers Dismiss / Snooze and closes itself when ringing ends.
 */
class AlarmRingActivity : ComponentActivity() {

    /** Set while Dismiss waits for unlock; the "ringing ended" auto-close must not cut that short. */
    private var handlingDismiss = false

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        // Fallback path: the receiver couldn't start the service, so start it now that we're visible.
        if (savedInstanceState == null && RingState.current.value == null && alarmId >= 0) {
            ContextCompat.startForegroundService(this, AlarmRingService.ringIntent(this, alarmId))
        }
        // Back must not dismiss an alarm by accident.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        closeWhenRingingEnds()

        setContent {
            KairoTheme {
                val ringing by RingState.current.collectAsStateWithLifecycle()
                val plan = ringing?.plan
                AlarmRingScreen(
                    clock = rememberClock(),
                    label = plan?.label.orEmpty(),
                    firstItem = rememberFirstItem(),
                    snoozeMinutes = plan?.snoozeMinutes ?: 0,
                    snoozesLeft = plan?.let(RingPolicy::snoozesLeft) ?: 0,
                    onSnooze = { startService(AlarmRingService.snoozeIntent(this)) },
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun closeWhenRingingEnds() {
        lifecycleScope.launch {
            RingState.current.filterNotNull().first()
            RingState.current.first { it == null }
            if (!handlingDismiss) finish()
        }
    }

    /**
     * Dismiss, then (if this alarm wants it) open the morning briefing. On a locked phone we ask
     * for unlock first, because the briefing shows personal data and isn't shown over the lock screen.
     */
    private fun dismiss() {
        val openBriefing = RingState.current.value?.plan?.openBriefingOnDismiss == true
        handlingDismiss = openBriefing
        startService(AlarmRingService.dismissIntent(this))
        if (!openBriefing) {
            finish()
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            openBriefing()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = openBriefing()
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    private fun openBriefing() {
        // BriefingActivity only speaks once it is resumed, so TTS never starts behind the lock screen.
        startActivity(BriefingActivity.intent(this))
        finish()
    }

    /** Volume keys must not silence the alarm (stock clock behaviour); swallow them here. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode in VOLUME_KEYS) true else super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode in VOLUME_KEYS) true else super.onKeyUp(keyCode, event)

    @Composable
    private fun rememberClock(): LocalTime {
        val time by produceState(LocalTime.now()) {
            while (true) {
                value = LocalTime.now()
                delay(CLOCK_TICK_MS)
            }
        }
        return time
    }

    /** Today's first remaining item; unavailable before the first unlock after a reboot (encrypted storage). */
    @Composable
    private fun rememberFirstItem(): String? {
        val item by produceState<String?>(null) {
            if (!isUserUnlocked()) return@produceState
            val c = (application as KairoApp).container
            DayTimelineSource(c.taskRepository, c.timetableRepository, c.roleRepository, c.dateProvider).today().collect { day ->
                value = Upcoming.remaining(day.entries, c.dateProvider.nowMinuteOfDay()).firstOrNull()?.title
            }
        }
        return item
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val CLOCK_TICK_MS = 1_000L
        private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmRingActivity::class.java).putExtra(EXTRA_ALARM_ID, alarmId)
    }
}
