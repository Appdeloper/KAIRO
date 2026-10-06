package com.kairo.app.ui

import com.kairo.app.domain.TimelineEntry
import com.kairo.app.ui.today.NowSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowSummaryTest {
    private val entries: List<TimelineEntry> = PreviewData.todayState.entries // 9-10 DBMS, 11-13 OS lab, 14-15 done, 16-16:45 reel, 20-21:30 report

    @Test fun duringALecture_showsItAndWhatsNext() {
        val s = NowSummary.from(entries, 9 * 60 + 20)
        assertEquals("DBMS lecture", s.current?.title)
        assertEquals(40, s.minutesLeftInCurrent)
        assertEquals("OS lab", s.next?.title)
        assertEquals(100, s.minutesUntilNext)
    }

    @Test fun betweenItems_hasOnlyNext_andSkipsDoneTasks() {
        val s = NowSummary.from(entries, 13 * 60 + 30)
        assertNull(s.current)
        assertEquals("Edit reel #12", s.next?.title) // the 2 PM task is done, so it's skipped
    }

    @Test fun afterEverything_isEmpty() {
        assertTrue(NowSummary.from(entries, 23 * 60).isEmpty)
    }

    @Test fun nowLineSitsBeforeTheFirstLaterItem() {
        assertEquals(0, NowSummary.nowLineIndex(entries, 8 * 60))
        assertEquals(1, NowSummary.nowLineIndex(entries, 9 * 60 + 30))
        assertEquals(entries.size, NowSummary.nowLineIndex(entries, 23 * 60))
    }
}
