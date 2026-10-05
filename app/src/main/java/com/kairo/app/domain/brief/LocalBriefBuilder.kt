package com.kairo.app.domain.brief

import com.kairo.app.domain.DayPart
import com.kairo.app.domain.Greeting
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.plan.PlanState
import com.kairo.app.domain.plan.Scheduler
import com.kairo.app.domain.plan.Slot

/** Wording lives in resources; the builder only decides what to say. */
interface BriefStrings {
    fun greeting(dayPart: DayPart, firstName: String): String
    fun emptyDay(dayPart: DayPart): String
    fun summary(remainingCount: Int, nextTitle: String, nextStartMinute: Int, minutesUntil: Int): String
    fun gapSuggestion(gapMinutes: Int): String
    fun ifThen(nextTitle: String): String
}

/**
 * The offline briefing: never empty, never an error. Used when AI is off, unconfigured, offline,
 * slow or over quota, and shown instantly while a cloud brief is on its way.
 */
object LocalBriefBuilder {
    /** Shorter gaps aren't worth recommending for focused work. */
    const val MIN_GAP_MINUTES = 30

    fun build(state: PlanState, firstName: String, strings: BriefStrings): Brief {
        val dayPart = Greeting.dayPartFor(state.nowMinute)
        val entries = TimelineBuilder.build(state.blocksOn(state.today), state.tasksOn(state.today), state.roles)
        val remaining = Upcoming.remaining(entries, state.nowMinute)
        val next = Upcoming.next(entries, state.nowMinute)
        val gap = firstGap(state)
        return Brief(
            greeting = strings.greeting(dayPart, firstName),
            summary = if (next == null) {
                strings.emptyDay(dayPart)
            } else {
                strings.summary(remaining.size, next.entry.title, next.entry.startMinute!!, next.minutesUntil)
            },
            bestGap = gap?.let { BestGap(it.startMinute, it.endMinute, strings.gapSuggestion(it.length)) },
            ifThenPlans = next?.let { listOf(strings.ifThen(it.entry.title)) }.orEmpty(),
        )
    }

    /** First usable free window from now until bedtime, using the scheduler's own slot rules. */
    fun freeSlots(state: PlanState): List<Slot> = Scheduler.findFreeSlots(
        date = state.today,
        fixedBlocks = state.blocksOn(state.today),
        scheduledTasks = state.tasksOn(state.today),
        dayStartMinute = state.earliestStartOn(state.today),
        dayEndMinute = state.dayEndMinute,
        bufferMinutes = state.bufferMinutes,
        alarmMinutes = state.alarmsOn(state.today),
    )

    private fun firstGap(state: PlanState): Slot? = freeSlots(state).firstOrNull { it.length >= MIN_GAP_MINUTES }
}
