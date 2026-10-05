package com.kairo.app.service.focus

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.prefs.FocusPrefsRepository
import com.kairo.app.data.repository.FocusRepository
import com.kairo.app.data.repository.FocusRepository.Recovered
import com.kairo.app.data.repository.FocusRepository.StartResult
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.domain.focus.StartCheck
import com.kairo.app.util.AppVisibility
import com.kairo.app.util.parseHexColorArgb
import kotlinx.coroutines.flow.first

/**
 * Glue between the stored session and what the system shows: the notification, the end alarm, the
 * progress tick and DND. No foreground service: the system draws the countdown and AlarmManager
 * wakes us at the end, so nothing of ours has to stay alive in between.
 */
class FocusEngine(
    private val context: Context,
    private val repository: FocusRepository,
    private val prefs: FocusPrefsRepository,
    private val roles: RoleRepository,
) {
    private val alarms = FocusAlarms(context)
    val dnd = FocusDnd(context, prefs)

    /** Only from visible UI (the Today sheet); refused otherwise rather than starting unseen. */
    suspend fun start(taskId: Long?, label: String, roleId: Long?, minutes: Int): StartResult {
        if (!AppVisibility.isAppVisible) return StartResult.Refused(StartCheck.NotFromVisibleUi)
        val result = repository.start(taskId, label, roleId, minutes)
        if (result is StartResult.Started) {
            prefs.clearDismissed()
            FocusNotifications.cancelEnded(context)
            show(result.session)
            dnd.applyIfWanted()
            Log.i(TAG, "focus started id=${result.session.id} minutes=$minutes")
        }
        return result
    }

    suspend fun extend(sessionId: Long): FocusSession? {
        val extended = repository.extend(sessionId) ?: return null
        // Extend is an explicit request, so the Live Update may come back even if it was swiped away.
        prefs.clearDismissed()
        show(extended)
        return extended
    }

    /**
     * Ends the session. Cleanup runs even if it had already ended (a duplicate tap, a racing alarm),
     * but the "next step" notification is only posted by whoever actually ended it.
     */
    suspend fun finish(sessionId: Long, outcome: FocusOutcome, reason: EndReason?): FocusSession? {
        val endedOnTime = reason == EndReason.TIME_UP || reason == EndReason.RECOVERED
        val ended = repository.finish(sessionId, outcome, endedOnTime)
        cleanUp()
        if (ended != null && reason != null) FocusNotifications.postEnded(context, ended, reason)
        Log.i(TAG, "focus finish id=$sessionId outcome=${ended?.outcome ?: "already-ended"} reason=$reason")
        return ended
    }

    /** The end alarm. If the session was extended after this alarm was set, just re-arm for the new end. */
    suspend fun onTimeUp(sessionId: Long) {
        val session = repository.find(sessionId)?.takeIf { it.outcome == FocusOutcome.RUNNING } ?: return
        val remaining = FocusTiming.remainingMillis(session, repository.now())
        if (remaining > EARLY_TOLERANCE_MS) {
            alarms.scheduleEnd(session.id, FocusTiming.endElapsedMillis(session, repository.now()))
            return
        }
        finish(sessionId, FocusOutcome.DONE, EndReason.TIME_UP)
    }

    suspend fun onTick(sessionId: Long) {
        val session = repository.find(sessionId)?.takeIf { it.outcome == FocusOutcome.RUNNING } ?: return
        if (prefs.current().dismissedSessionId != session.id) show(session)
    }

    suspend fun onDismissed(sessionId: Long) = prefs.markDismissed(sessionId)

    /**
     * After a reboot, an app update, or app open (covers force-stop, which cancels our alarms):
     * resume a running session, or close one whose end passed and ask for the next step.
     */
    suspend fun reconcile() {
        when (val recovered = repository.recover()) {
            is Recovered.Resumed -> {
                show(recovered.session, repost = prefs.current().dismissedSessionId != recovered.session.id)
                Log.i(TAG, "focus resumed id=${recovered.session.id}")
            }
            is Recovered.Closed -> {
                cleanUp()
                FocusNotifications.postEnded(context, recovered.session, EndReason.RECOVERED)
                Log.i(TAG, "focus closed after recovery id=${recovered.session.id}")
            }
            null -> if (prefs.current().dndApplied) dnd.restore()
        }
    }

    suspend fun saveNextStep(session: FocusSession, text: String) {
        repository.saveNextStep(session, text)
        FocusNotifications.cancelEnded(context)
    }

    fun dismissEndedNotification() = FocusNotifications.cancelEnded(context)

    private suspend fun show(session: FocusSession, repost: Boolean = true) {
        val now = repository.now()
        if (repost) FocusNotifications.showOngoing(context, session, roleColor(session.roleId), now)
        alarms.scheduleEnd(session.id, FocusTiming.endElapsedMillis(session, now))
        val step = (FocusTiming.plannedDurationMillis(session) / TICKS_PER_SESSION).coerceAtLeast(MIN_TICK_MS)
        alarms.scheduleTick(session.id, now.elapsedMillis + step)
    }

    private suspend fun cleanUp() {
        alarms.cancelAll()
        FocusNotifications.cancelOngoing(context)
        dnd.restore()
    }

    private suspend fun roleColor(roleId: Long?): Int =
        roles.allRoles().first().firstOrNull { it.id == roleId }?.let { parseHexColorArgb(it.colorHex) } ?: Color.GRAY

    private companion object {
        const val TAG = "KairoFocus"
        const val TICKS_PER_SESSION = 30
        const val MIN_TICK_MS = 60_000L
        const val EARLY_TOLERANCE_MS = 30_000L
    }
}
