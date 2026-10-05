package com.kairo.app.alarm

import com.kairo.app.data.local.AlarmType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RingPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(LocalDateTime.of(2026, 10, 5, 7, 0), zone)
    private val base = AlarmPlan(1, "Wake", AlarmType.WAKE, 7 * 60, AlarmDays.WEEKDAYS, enabled = true, snoozeMinutes = 5, maxSnoozes = 3)

    @Test
    fun snoozeCountsUpToTheLimit() {
        var p = base
        var t: Instant = now.toInstant()
        repeat(3) { i ->
            assertTrue(RingPolicy.canSnooze(p))
            p = RingPolicy.afterSnooze(p, t)!!
            assertEquals(i + 1, p.snoozeCount)
            assertEquals(t.plusSeconds(300).toEpochMilli(), p.snoozedUntilMillis)
            t = t.plusSeconds(300)
        }
        assertEquals(0, RingPolicy.snoozesLeft(p))
        assertFalse(RingPolicy.canSnooze(p))
        assertNull("4th snooze refused", RingPolicy.afterSnooze(p, t))
    }

    @Test
    fun zeroSnoozesMeansNoSnooze() {
        assertNull(RingPolicy.afterSnooze(base.copy(maxSnoozes = 0), now.toInstant()))
    }

    @Test
    fun dismissResetsSnoozeAndKeepsRepeatingAlarmOn() {
        val snoozed = RingPolicy.afterSnooze(base, now.toInstant())!!
        val dismissed = RingPolicy.afterDismiss(snoozed, now)
        assertEquals(0, dismissed.snoozeCount)
        assertNull(dismissed.snoozedUntilMillis)
        assertTrue(dismissed.enabled)
    }

    @Test
    fun dismissTurnsOneShotOff() {
        assertFalse(RingPolicy.afterDismiss(base.copy(daysMask = AlarmDays.ONE_SHOT), now).enabled)
    }

    @Test
    fun dismissClearsAPassedSkip() {
        val skipToday = base.copy(skipNextOnce = true, skipDateEpochDay = now.toLocalDate().toEpochDay())
        val later = now.plusHours(1)
        assertFalse(RingPolicy.afterDismiss(skipToday, later).skipNextOnce)
        val skipTomorrow = base.copy(skipNextOnce = true, skipDateEpochDay = now.toLocalDate().plusDays(1).toEpochDay())
        assertTrue(RingPolicy.afterDismiss(skipTomorrow, later).skipNextOnce)
    }
}
