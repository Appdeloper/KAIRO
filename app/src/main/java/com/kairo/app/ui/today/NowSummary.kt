package com.kairo.app.ui.today

import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.brief.Upcoming

/** What the Today "Now" card shows: what's happening, what's next, and the countdowns. */
data class NowSummary(
    val current: TimelineEntry?,
    val minutesLeftInCurrent: Int?,
    val next: TimelineEntry?,
    val minutesUntilNext: Int?,
) {
    val isEmpty: Boolean get() = current == null && next == null

    companion object {
        /** Reuses Upcoming's rules (not done, not skipped, has a time, not finished) so Today and the briefing agree. */
        fun from(entries: List<TimelineEntry>, nowMinute: Int): NowSummary {
            val remaining = Upcoming.remaining(entries, nowMinute)
            val current = remaining.firstOrNull { it.startMinute!! <= nowMinute }
            val next = remaining.firstOrNull { it.startMinute!! > nowMinute }
            return NowSummary(
                current = current,
                minutesLeftInCurrent = current?.endMinute?.minus(nowMinute),
                next = next,
                minutesUntilNext = next?.startMinute?.minus(nowMinute),
            )
        }

        /** Index in [entries] before which the amber now-line goes (entries.size = after the last one). */
        fun nowLineIndex(entries: List<TimelineEntry>, nowMinute: Int): Int {
            val index = entries.indexOfFirst { e -> e.startMinute != null && e.startMinute!! > nowMinute }
            return if (index >= 0) index else entries.count { it.startMinute != null }
        }
    }
}
