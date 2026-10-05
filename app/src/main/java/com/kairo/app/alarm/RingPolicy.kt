package com.kairo.app.alarm

import java.time.Instant
import java.time.ZonedDateTime

/** What snoozing, dismissing and auto-silencing do to an alarm. Pure, so the rules are tested. */
object RingPolicy {
    fun snoozesLeft(plan: AlarmPlan): Int = (plan.maxSnoozes - plan.snoozeCount).coerceAtLeast(0)
    fun canSnooze(plan: AlarmPlan): Boolean = snoozesLeft(plan) > 0

    /** Null when the snooze limit is reached; the UI disables the button before that happens. */
    fun afterSnooze(plan: AlarmPlan, now: Instant): AlarmPlan? {
        if (!canSnooze(plan)) return null
        return plan.copy(
            snoozeCount = plan.snoozeCount + 1,
            snoozedUntilMillis = now.plusSeconds(plan.snoozeMinutes * 60L).toEpochMilli(),
        )
    }

    /**
     * Dismiss (or auto-silence) ends this ringing: snooze state resets, a one-shot alarm turns
     * itself off, and a skip whose day has passed is cleared.
     */
    fun afterDismiss(plan: AlarmPlan, now: ZonedDateTime): AlarmPlan {
        val cleared = plan.copy(snoozeCount = 0, snoozedUntilMillis = null)
        val withSkip = if (AlarmTimes.skipIsStale(cleared, now)) cleared.copy(skipNextOnce = false, skipDateEpochDay = null) else cleared
        return if (plan.daysMask == AlarmDays.ONE_SHOT) withSkip.copy(enabled = false) else withSkip
    }

    /** Sets "skip next" on the occurrence that would ring next right now. */
    fun skipNext(plan: AlarmPlan, now: ZonedDateTime): AlarmPlan {
        val next = AlarmTimes.nextOccurrence(plan.copy(skipNextOnce = false), now) ?: return plan
        return plan.copy(skipNextOnce = true, skipDateEpochDay = next.toLocalDate().toEpochDay())
    }

    fun unskip(plan: AlarmPlan): AlarmPlan = plan.copy(skipNextOnce = false, skipDateEpochDay = null)
}
