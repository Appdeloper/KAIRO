package com.kairo.app.domain.focus

import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import kotlin.math.roundToInt

/** A reading of both clocks at one moment, plus which boot we're on. */
data class ClockReading(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int)

/**
 * Pure timing rules for a focus session. Same boot: trust elapsed realtime (immune to clock
 * changes). Different boot: elapsed restarted from zero, so fall back to wall-clock time.
 */
object FocusTiming {
    const val MINUTE_MS = 60_000L
    const val EXTEND_MINUTES = 10

    fun newSession(
        taskId: Long?,
        label: String,
        roleId: Long?,
        minutes: Int,
        now: ClockReading,
    ): FocusSession = FocusSession(
        taskId = taskId,
        blockLabel = label,
        roleId = roleId,
        startAtEpochMillis = now.wallMillis,
        plannedEndEpochMillis = now.wallMillis + minutes * MINUTE_MS,
        startElapsedMillis = now.elapsedMillis,
        bootCount = now.bootCount,
        plannedMinutes = minutes,
    )

    fun plannedDurationMillis(s: FocusSession): Long = s.plannedEndEpochMillis - s.startAtEpochMillis

    fun elapsedMillis(s: FocusSession, now: ClockReading): Long {
        val raw = if (sameBoot(s, now)) now.elapsedMillis - s.startElapsedMillis else now.wallMillis - s.startAtEpochMillis
        return raw.coerceAtLeast(0)
    }

    /** Never negative: "0 left" once time is up, however late we look. */
    fun remainingMillis(s: FocusSession, now: ClockReading): Long =
        (plannedDurationMillis(s) - elapsedMillis(s, now)).coerceAtLeast(0)

    fun isOver(s: FocusSession, now: ClockReading): Boolean = remainingMillis(s, now) == 0L

    /** Where the end falls on this boot's elapsed clock, for an ELAPSED_REALTIME_WAKEUP alarm. */
    fun endElapsedMillis(s: FocusSession, now: ClockReading): Long = now.elapsedMillis + remainingMillis(s, now)

    /** The end in wall-clock terms as seen right now, for the notification's countdown. */
    fun endWallMillis(s: FocusSession, now: ClockReading): Long = now.wallMillis + remainingMillis(s, now)

    /** 0..1 for progress bars. */
    fun progress(s: FocusSession, now: ClockReading): Float {
        val total = plannedDurationMillis(s)
        return if (total <= 0) 1f else (elapsedMillis(s, now).toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Adds time to the end. If the end already slipped past (a late alarm), extend from now so the
     * user really gets the minutes they asked for.
     */
    fun extend(s: FocusSession, now: ClockReading, minutes: Int = EXTEND_MINUTES): FocusSession {
        val add = minutes * MINUTE_MS
        val newDuration = maxOf(plannedDurationMillis(s), elapsedMillis(s, now)) + add
        return s.copy(
            plannedEndEpochMillis = s.startAtEpochMillis + newDuration,
            extendedMinutes = s.extendedMinutes + minutes,
        )
    }

    /** DONE becomes EXTENDED when the user took extra time, so insights can tell them apart. */
    fun finishedOutcome(s: FocusSession, requested: FocusOutcome): FocusOutcome = when {
        requested == FocusOutcome.DROPPED -> FocusOutcome.DROPPED
        s.extendedMinutes > 0 -> FocusOutcome.EXTENDED
        else -> FocusOutcome.DONE
    }

    /**
     * When the session really ended, in wall-clock time. Ending on time (the end alarm, or a
     * recovery after the end passed) uses the planned end, not the moment we noticed.
     */
    fun actualEndWallMillis(s: FocusSession, now: ClockReading, endedOnTime: Boolean): Long =
        if (endedOnTime) s.plannedEndEpochMillis else s.startAtEpochMillis + elapsedMillis(s, now)

    fun minutesBetween(fromMillis: Long, toMillis: Long): Int = ((toMillis - fromMillis).coerceAtLeast(0) / MINUTE_MS.toDouble()).roundToInt()

    private fun sameBoot(s: FocusSession, now: ClockReading) = s.bootCount == now.bootCount && now.elapsedMillis >= s.startElapsedMillis
}

/** What to do with a RUNNING session after a reboot, app update or force-stop. */
sealed interface RecoveryDecision {
    /** Still time left: re-anchor to this boot, then re-post the notification and end alarm. */
    data class Resume(val reanchored: FocusSession) : RecoveryDecision

    /** The end passed while we weren't running: close as finished and ask for the next step. Never lost. */
    data object CloseAsFinished : RecoveryDecision
}

object FocusRecovery {
    fun decide(s: FocusSession, now: ClockReading): RecoveryDecision {
        if (FocusTiming.isOver(s, now)) return RecoveryDecision.CloseAsFinished
        // Keep elapsed time continuous across the reboot: pretend the start happened on this boot.
        val elapsed = FocusTiming.elapsedMillis(s, now)
        return RecoveryDecision.Resume(s.copy(startElapsedMillis = now.elapsedMillis - elapsed, bootCount = now.bootCount))
    }
}
