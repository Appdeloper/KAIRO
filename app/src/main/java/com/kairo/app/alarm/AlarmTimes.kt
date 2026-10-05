package com.kairo.app.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When does an alarm ring next? Pure java.time, evaluated in the zone of [now], so rescheduling
 * after a time-zone change keeps wall-clock times ("7:00 local") rather than instants.
 */
object AlarmTimes {
    /** Two weeks covers any weekly pattern even with one occurrence skipped and a lecture skip. */
    private const val SEARCH_DAYS = 15L

    fun nextOccurrence(plan: AlarmPlan, now: ZonedDateTime): ZonedDateTime? {
        if (!plan.enabled) return null
        var skipUsed = false
        for (offset in 0..SEARCH_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            if (!ringsOn(plan, date)) continue
            // ZonedDateTime.of moves a time inside a DST gap forward, so a 2:30 alarm on a
            // spring-forward night rings at 3:30 instead of vanishing.
            val at = ZonedDateTime.of(date, LocalTime.of(plan.minuteOfDay / 60, plan.minuteOfDay % 60), now.zone)
            if (!at.isAfter(now)) continue
            if (isSkipped(plan, date, skipUsed)) {
                skipUsed = true
                continue
            }
            return at
        }
        return null
    }

    /** The next moment this alarm should fire: its next occurrence, or a pending snooze if sooner. */
    fun nextTrigger(plan: AlarmPlan, now: ZonedDateTime): Instant? {
        val occurrence = nextOccurrence(plan, now)?.toInstant()
        val snooze = plan.snoozedUntilMillis?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now.toInstant()) }
        return listOfNotNull(occurrence, snooze).minOrNull()
    }

    fun nextAcross(plans: List<AlarmPlan>, now: ZonedDateTime): Pair<AlarmPlan, Instant>? =
        plans.mapNotNull { p -> nextTrigger(p, now)?.let { p to it } }.minByOrNull { it.second }

    /**
     * Alarm minutes per date for the planner (PlanState.alarmsByDate), so no task is ever placed
     * on top of an alarm. Repeating alarms block their time on every matching day in the window.
     */
    fun minutesByDate(plans: List<AlarmPlan>, now: ZonedDateTime, days: Int): Map<LocalDate, List<Int>> {
        val result = mutableMapOf<LocalDate, MutableList<Int>>()
        for (plan in plans.filter { it.enabled }) {
            if (plan.daysMask == AlarmDays.ONE_SHOT) {
                nextOccurrence(plan, now)?.let { result.getOrPut(it.toLocalDate()) { mutableListOf() } += plan.minuteOfDay }
                continue
            }
            for (offset in 0 until days) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (ringsOn(plan, date)) result.getOrPut(date) { mutableListOf() } += plan.minuteOfDay
            }
        }
        return result.mapValues { (_, minutes) -> minutes.sorted() }
    }

    /** True once the skipped occurrence has passed, so the flag can be cleared. */
    fun skipIsStale(plan: AlarmPlan, now: ZonedDateTime): Boolean {
        if (!plan.skipNextOnce) return false
        val skipDate = plan.skipDateEpochDay?.let(LocalDate::ofEpochDay) ?: return false
        val skippedAt = ZonedDateTime.of(skipDate, LocalTime.of(plan.minuteOfDay / 60, plan.minuteOfDay % 60), now.zone)
        return !skippedAt.isAfter(now)
    }

    private fun ringsOn(plan: AlarmPlan, date: LocalDate): Boolean =
        (plan.daysMask == AlarmDays.ONE_SHOT || AlarmDays.contains(plan.daysMask, date.dayOfWeek)) &&
            date.toEpochDay() !in plan.skipEpochDays

    /** A dated skip matches only its day; an undated (legacy) skip drops the first upcoming one. */
    private fun isSkipped(plan: AlarmPlan, date: LocalDate, skipUsed: Boolean): Boolean {
        if (!plan.skipNextOnce || skipUsed) return false
        val skipDate = plan.skipDateEpochDay ?: return true
        return skipDate == date.toEpochDay()
    }
}
