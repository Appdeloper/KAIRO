package com.kairo.app.domain.focus

import com.kairo.app.data.local.FocusOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusRecoveryAndRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val startWall = ZonedDateTime.of(2026, 10, 5, 16, 10, 0, 0, zone).toInstant().toEpochMilli()
    private val start = ClockReading(startWall, elapsedMillis = 9_000_000, bootCount = 4)
    private val session = FocusTiming.newSession(taskId = 2, label = "Reel", roleId = 4, minutes = 25, now = start)

    @Test
    fun reboot_midSession_resumesAndReanchorsToTheNewBoot() {
        val afterReboot = ClockReading(startWall + 10 * MIN, elapsedMillis = 40_000, bootCount = 5)
        val decision = FocusRecovery.decide(session, afterReboot)
        assertTrue(decision is RecoveryDecision.Resume)
        val resumed = (decision as RecoveryDecision.Resume).reanchored
        assertEquals(5, resumed.bootCount)
        // From here on the elapsed clock drives the countdown again, continuing where it left off.
        assertEquals(15 * MIN, FocusTiming.remainingMillis(resumed, afterReboot))
        val aMinuteLater = afterReboot.copy(wallMillis = afterReboot.wallMillis + MIN, elapsedMillis = afterReboot.elapsedMillis + MIN)
        assertEquals(14 * MIN, FocusTiming.remainingMillis(resumed, aMinuteLater))
    }

    @Test
    fun reboot_afterTheEnd_closesInsteadOfLosingIt() {
        val afterReboot = ClockReading(startWall + 40 * MIN, elapsedMillis = 40_000, bootCount = 5)
        assertEquals(RecoveryDecision.CloseAsFinished, FocusRecovery.decide(session, afterReboot))
    }

    @Test
    fun reboot_exactlyAtTheEnd_closes() {
        val atEnd = ClockReading(startWall + 25 * MIN, elapsedMillis = 1_000, bootCount = 5)
        assertEquals(RecoveryDecision.CloseAsFinished, FocusRecovery.decide(session, atEnd))
    }

    @Test
    fun forceStop_sameBoot_resumesFromElapsedClock() {
        val sameBoot = ClockReading(startWall + 5 * MIN, start.elapsedMillis + 5 * MIN, bootCount = 4)
        val resumed = (FocusRecovery.decide(session, sameBoot) as RecoveryDecision.Resume).reanchored
        assertEquals(session.startElapsedMillis, resumed.startElapsedMillis)
    }

    @Test
    fun unknownBootCount_fallsBackToWallClock() {
        val hidden = FocusTiming.newSession(1, "x", null, 25, start.copy(bootCount = -1))
        val afterReboot = ClockReading(startWall + 10 * MIN, elapsedMillis = 1_000, bootCount = -1)
        // elapsed went backwards, so it can't be the same boot even though both counts are -1.
        assertEquals(15 * MIN, FocusTiming.remainingMillis(hidden, afterReboot))
    }

    @Test
    fun onlyOneRunning() {
        assertEquals(StartCheck.AlreadyRunning(session), FocusRules.canStart(session, 25))
        assertEquals(StartCheck.Ok, FocusRules.canStart(null, 25))
    }

    @Test
    fun durationBounds() {
        assertEquals(StartCheck.InvalidDuration, FocusRules.canStart(null, 0))
        assertEquals(StartCheck.InvalidDuration, FocusRules.canStart(null, 241))
        assertEquals("a 2-minute sprint is allowed", StartCheck.Ok, FocusRules.canStart(null, 2))
        assertEquals(StartCheck.Ok, FocusRules.canStart(null, 240))
    }

    @Test
    fun initialDuration_isBlockLengthOrUserDefault() {
        assertEquals(50, FocusDurations.initialFor(540, 590, userDefault = 25))
        assertEquals("anytime task", 45, FocusDurations.initialFor(null, null, userDefault = 45))
        assertEquals("3-hour lab is capped", 240, FocusDurations.initialFor(600, 900, userDefault = 25))
        assertEquals("broken block uses default", 25, FocusDurations.initialFor(600, 600, userDefault = 25))
    }

    @Test
    fun customSlider_snapsFineThenCoarse() {
        assertEquals(1, FocusDurations.snap(0.2f))
        assertEquals(2, FocusDurations.snap(2.4f))
        assertEquals(14, FocusDurations.snap(14.4f))
        assertEquals(35, FocusDurations.snap(33.1f))
        assertEquals(240, FocusDurations.snap(260f))
    }

    @Test
    fun log_forDone() {
        val ended = session.copy(outcome = FocusOutcome.DONE, actualEndEpochMillis = session.plannedEndEpochMillis)
        val log = FocusLogs.from(ended, zone)
        assertEquals(16, log.hourOfDay)
        assertEquals(4L, log.roleId)
        assertEquals(25, log.plannedMinutes)
        assertEquals(25, log.actualMinutes)
        assertTrue(log.completed)
    }

    @Test
    fun log_forExtendedTotal() {
        val extended = FocusTiming.extend(FocusTiming.extend(session, start), start)
        val ended = extended.copy(outcome = FocusOutcome.EXTENDED, actualEndEpochMillis = extended.plannedEndEpochMillis)
        val log = FocusLogs.from(ended, zone)
        assertEquals(25, log.plannedMinutes)
        assertEquals(45, log.actualMinutes)
        assertTrue(log.completed)
    }

    @Test
    fun log_forDropped() {
        val ended = session.copy(outcome = FocusOutcome.DROPPED, actualEndEpochMillis = startWall + 7 * MIN + 20_000)
        val log = FocusLogs.from(ended, zone)
        assertEquals(7, log.actualMinutes)
        assertFalse(log.completed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun log_refusesRunningSession() {
        FocusLogs.from(session, zone)
    }

    private companion object {
        const val MIN = 60_000L
    }
}
