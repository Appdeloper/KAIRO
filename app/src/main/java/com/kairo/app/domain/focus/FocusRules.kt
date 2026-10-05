package com.kairo.app.domain.focus

import com.kairo.app.data.local.FocusLog
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

sealed interface StartCheck {
    data object Ok : StartCheck
    data class AlreadyRunning(val current: FocusSession) : StartCheck
    data object InvalidDuration : StartCheck

    /** Rule: a session only starts from something the user is looking at. */
    data object NotFromVisibleUi : StartCheck
}

object FocusRules {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240

    /** Only one session at a time; the DAO enforces the same rule again inside a transaction. */
    fun canStart(running: FocusSession?, minutes: Int): StartCheck = when {
        running != null -> StartCheck.AlreadyRunning(running)
        minutes !in MIN_MINUTES..MAX_MINUTES -> StartCheck.InvalidDuration
        else -> StartCheck.Ok
    }
}

object FocusDurations {
    val CHIPS = listOf(15, 25, 45, 60)
    const val DEFAULT_MINUTES = 25
    private const val FINE_BELOW = 15
    private const val COARSE_STEP = 5

    /** Custom slider: 1-minute steps for short sprints, 5-minute steps once it's a real block. */
    fun snap(raw: Float): Int {
        val minutes = raw.roundToInt().coerceIn(FocusRules.MIN_MINUTES, FocusRules.MAX_MINUTES)
        return if (minutes < FINE_BELOW) minutes else (minutes / COARSE_STEP.toFloat()).roundToInt() * COARSE_STEP
    }

    /**
     * The sheet's starting value: the entry's own length when it has one, otherwise the user's
     * default from Settings. Clamped so a 3-hour lab doesn't start a 3-hour timer by accident.
     */
    fun initialFor(entryStartMinute: Int?, entryEndMinute: Int?, userDefault: Int): Int {
        val length = if (entryStartMinute != null && entryEndMinute != null) entryEndMinute - entryStartMinute else null
        return (length?.takeIf { it > 0 } ?: userDefault).coerceIn(FocusRules.MIN_MINUTES, FocusRules.MAX_MINUTES)
    }
}

object FocusLogs {
    /**
     * The log row for a finished session. plannedMinutes is the first pick (before Extend) and
     * actualMinutes the real time spent, so "extended" shows up as actual > planned.
     */
    fun from(ended: FocusSession, zone: ZoneId): FocusLog {
        val end = requireNotNull(ended.actualEndEpochMillis) { "log only finished sessions" }
        return FocusLog(
            hourOfDay = Instant.ofEpochMilli(ended.startAtEpochMillis).atZone(zone).hour,
            roleId = ended.roleId,
            plannedMinutes = ended.plannedMinutes,
            actualMinutes = FocusTiming.minutesBetween(ended.startAtEpochMillis, end),
            completed = ended.outcome == FocusOutcome.DONE || ended.outcome == FocusOutcome.EXTENDED,
        )
    }
}
