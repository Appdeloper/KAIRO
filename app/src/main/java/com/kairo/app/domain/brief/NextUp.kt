package com.kairo.app.domain.brief

import com.kairo.app.domain.TimelineEntry

data class NextUp(val entry: TimelineEntry, val minutesUntil: Int)

object Upcoming {
    /** Still worth showing: not done, not skipped, has a time, and hasn't finished yet. */
    fun remaining(entries: List<TimelineEntry>, nowMinute: Int): List<TimelineEntry> = entries.filter { e ->
        val done = (e as? TimelineEntry.TaskEntry)?.isDone == true
        val skipped = (e as? TimelineEntry.Block)?.skipped == true
        val end = e.endMinute
        !done && !skipped && e.startMinute != null && end != null && end > nowMinute
    }

    fun next(entries: List<TimelineEntry>, nowMinute: Int): NextUp? =
        remaining(entries, nowMinute).firstOrNull()?.let { NextUp(it, maxOf(0, it.startMinute!! - nowMinute)) }
}
