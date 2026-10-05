package com.kairo.app.domain.focus

import com.kairo.app.data.local.FocusOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimingTest {
    private val start = ClockReading(wallMillis = 1_000_000_000L, elapsedMillis = 50_000L, bootCount = 7)
    private val session = FocusTiming.newSession(taskId = 1, label = "Reel", roleId = 4, minutes = 25, now = start)

    private fun later(ms: Long, wallShift: Long = 0, boot: Int = 7) =
        ClockReading(start.wallMillis + ms + wallShift, start.elapsedMillis + ms, boot)

    @Test
    fun newSession_plansEndFromMinutes() {
        assertEquals(start.wallMillis + 25 * MIN, session.plannedEndEpochMillis)
        assertEquals(25, session.plannedMinutes)
        assertEquals(FocusOutcome.RUNNING, session.outcome)
    }

    @Test
    fun remaining_countsDownOnElapsedClock() {
        assertEquals(25 * MIN, FocusTiming.remainingMillis(session, start))
        assertEquals(15 * MIN, FocusTiming.remainingMillis(session, later(10 * MIN)))
        assertEquals(0.4f, FocusTiming.progress(session, later(10 * MIN)), 0.001f)
    }

    @Test
    fun remaining_ignoresWallClockChangesOnSameBoot() {
        // User moves the clock forward an hour, 10 minutes in: elapsed realtime still says 15 left.
        assertEquals(15 * MIN, FocusTiming.remainingMillis(session, later(10 * MIN, wallShift = 60 * MIN)))
    }

    @Test
    fun remaining_usesWallClockAfterReboot() {
        // New boot: elapsed restarted near zero, so only wall-clock time can be trusted.
        val afterReboot = ClockReading(start.wallMillis + 20 * MIN, elapsedMillis = 30_000, bootCount = 8)
        assertEquals(5 * MIN, FocusTiming.remainingMillis(session, afterReboot))
    }

    @Test
    fun remaining_neverNegative() {
        assertEquals(0, FocusTiming.remainingMillis(session, later(40 * MIN)))
        assertTrue(FocusTiming.isOver(session, later(25 * MIN)))
        assertFalse(FocusTiming.isOver(session, later(25 * MIN - 1)))
    }

    @Test
    fun endElapsed_and_endWall_pointAtTheEnd() {
        val now = later(5 * MIN)
        assertEquals(start.elapsedMillis + 25 * MIN, FocusTiming.endElapsedMillis(session, now))
        assertEquals(start.wallMillis + 25 * MIN, FocusTiming.endWallMillis(session, now))
    }

    @Test
    fun extend_addsTenMinutesToTheEnd() {
        val extended = FocusTiming.extend(session, later(20 * MIN))
        assertEquals(session.plannedEndEpochMillis + 10 * MIN, extended.plannedEndEpochMillis)
        assertEquals(10, extended.extendedMinutes)
        assertEquals("first pick is kept for the log", 25, extended.plannedMinutes)
        assertEquals(15 * MIN, FocusTiming.remainingMillis(extended, later(20 * MIN)))
    }

    @Test
    fun extend_twice_accumulates() {
        val twice = FocusTiming.extend(FocusTiming.extend(session, later(MIN)), later(2 * MIN))
        assertEquals(20, twice.extendedMinutes)
        assertEquals(45 * MIN, FocusTiming.plannedDurationMillis(twice))
    }

    @Test
    fun extend_afterALateAlarm_countsFromNow() {
        // The end alarm arrived 3 minutes late; Extend must still give a full 10 minutes from now.
        val extended = FocusTiming.extend(session, later(28 * MIN))
        assertEquals(10 * MIN, FocusTiming.remainingMillis(extended, later(28 * MIN)))
    }

    @Test
    fun finishedOutcome_marksExtendedSessions() {
        assertEquals(FocusOutcome.DONE, FocusTiming.finishedOutcome(session, FocusOutcome.DONE))
        val extended = FocusTiming.extend(session, later(MIN))
        assertEquals(FocusOutcome.EXTENDED, FocusTiming.finishedOutcome(extended, FocusOutcome.DONE))
        assertEquals(FocusOutcome.DROPPED, FocusTiming.finishedOutcome(extended, FocusOutcome.DROPPED))
    }

    @Test
    fun actualEnd_onTimeUsesPlannedEnd_earlyUsesNow() {
        assertEquals(session.plannedEndEpochMillis, FocusTiming.actualEndWallMillis(session, later(27 * MIN), endedOnTime = true))
        assertEquals(start.wallMillis + 12 * MIN, FocusTiming.actualEndWallMillis(session, later(12 * MIN), endedOnTime = false))
    }

    private companion object {
        const val MIN = 60_000L
    }
}
