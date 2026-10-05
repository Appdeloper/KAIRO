package com.kairo.app.alarm

import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.BlockSource
import com.kairo.app.data.local.FixedBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimesTest {
    private val india = ZoneId.of("Asia/Kolkata")

    /** Monday 5 Oct 2026. */
    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = india) =
        ZonedDateTime.of(LocalDateTime.of(2026, 10, day, hour, minute), zone)

    private fun plan(hour: Int, minute: Int = 0, mask: Int = AlarmDays.ONE_SHOT, enabled: Boolean = true) =
        AlarmPlan(id = 1, label = "", type = AlarmType.WAKE, minuteOfDay = hour * 60 + minute, daysMask = mask, enabled = enabled)

    @Test
    fun oneShot_laterToday() {
        assertEquals(at(5, 7, 30), AlarmTimes.nextOccurrence(plan(7, 30), at(5, 6)))
    }

    @Test
    fun oneShot_timeAlreadyPassedToday_ringsTomorrow() {
        assertEquals(at(6, 7), AlarmTimes.nextOccurrence(plan(7), at(5, 9)))
        // Exactly "now" counts as past, so an alarm that just fired doesn't reschedule onto itself.
        assertEquals(at(6, 7), AlarmTimes.nextOccurrence(plan(7), at(5, 7)))
    }

    @Test
    fun repeating_weekdaysSkipTheWeekend() {
        val weekdays = plan(7, mask = AlarmDays.WEEKDAYS)
        assertEquals(at(6, 7), AlarmTimes.nextOccurrence(weekdays, at(5, 8)))
        // Friday 9 Oct after the alarm -> Monday 12 Oct.
        assertEquals(at(12, 7), AlarmTimes.nextOccurrence(weekdays, at(9, 8)))
        val sundayOnly = plan(9, mask = AlarmDays.bit(java.time.DayOfWeek.SUNDAY))
        assertEquals(at(11, 9), AlarmTimes.nextOccurrence(sundayOnly, at(5, 8)))
    }

    @Test
    fun disabledNeverRings() {
        assertNull(AlarmTimes.nextOccurrence(plan(7, enabled = false), at(5, 6)))
        assertNull(AlarmTimes.nextTrigger(plan(7, enabled = false), at(5, 6)))
    }

    @Test
    fun skipNextOnce_skipsExactlyTheChosenDay() {
        val weekdays = plan(7, mask = AlarmDays.WEEKDAYS)
        val skipped = RingPolicy.skipNext(weekdays, at(5, 8))
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(), skipped.skipDateEpochDay)
        assertEquals("Tuesday skipped -> Wednesday", at(7, 7), AlarmTimes.nextOccurrence(skipped, at(5, 8)))
        // Rescheduled again later on Tuesday (e.g. after a reboot): Wednesday still rings, no double skip.
        assertEquals(at(7, 7), AlarmTimes.nextOccurrence(skipped, at(6, 9)))
        assertTrue(AlarmTimes.skipIsStale(skipped, at(6, 9)))
        assertFalse(AlarmTimes.skipIsStale(skipped, at(5, 23)))
        assertEquals(at(6, 7), AlarmTimes.nextOccurrence(RingPolicy.unskip(skipped), at(5, 8)))
    }

    @Test
    fun skipNextOnce_withoutDateSkipsFirstUpcoming() {
        val legacy = plan(7, mask = AlarmDays.EVERY_DAY).copy(skipNextOnce = true)
        assertEquals(at(7, 7), AlarmTimes.nextOccurrence(legacy, at(5, 8)))
    }

    @Test
    fun timezoneChange_keepsLocalWallClockTime() {
        val wake = plan(7, mask = AlarmDays.EVERY_DAY)
        val newYork = ZoneId.of("America/New_York")
        val inNewYork = AlarmTimes.nextOccurrence(wake, at(5, 6, zone = newYork))!!
        assertEquals(7, inNewYork.hour)
        assertEquals(newYork, inNewYork.zone)
        // Same instant viewed from India: the alarm is at 7:00 India time, a different instant.
        val sameInstantInIndia = at(5, 6, zone = newYork).withZoneSameInstant(india)
        assertEquals(7, AlarmTimes.nextOccurrence(wake, sameInstantInIndia)!!.hour)
    }

    @Test
    fun dstGap_movesForwardInsteadOfVanishing() {
        val newYork = ZoneId.of("America/New_York")
        // 8 Mar 2026: clocks jump 2:00 -> 3:00 in New York.
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 1, 0), newYork)
        val result = AlarmTimes.nextOccurrence(plan(2, 30), now)!!
        assertEquals(LocalDate.of(2026, 3, 8), result.toLocalDate())
        assertEquals(3, result.hour)
    }

    @Test
    fun snoozeWinsWhenSooner() {
        val snoozed = plan(7, mask = AlarmDays.EVERY_DAY).copy(snoozedUntilMillis = at(5, 7, 5).toInstant().toEpochMilli())
        assertEquals(at(5, 7, 5).toInstant(), AlarmTimes.nextTrigger(snoozed, at(5, 7, 1)))
        // An expired snooze is ignored.
        assertEquals(at(6, 7).toInstant(), AlarmTimes.nextTrigger(snoozed, at(5, 8)))
    }

    @Test
    fun nextAcrossPicksTheSoonest() {
        val a = plan(9).copy(id = 1)
        val b = plan(7, 30).copy(id = 2)
        assertEquals(2L, AlarmTimes.nextAcross(listOf(a, b), at(5, 6))!!.first.id)
    }

    @Test
    fun blockAlarm_followsLectureAndItsSkips() {
        val lecture = FixedBlock(id = 9, title = "DBMS", roleId = 1, dayOfWeek = 3, startMinute = 9 * 60, endMinute = 10 * 60, source = BlockSource.MANUAL)
        val alarm = Alarm(id = 4, hour = 0, minute = 0, type = AlarmType.BLOCK, linkedBlockId = 9, offsetMinutesBeforeBlock = 45)
        val p = AlarmPlans.resolve(alarm, lecture)
        assertEquals(8 * 60 + 15, p.minuteOfDay)
        assertEquals(at(7, 8, 15), AlarmTimes.nextOccurrence(p, at(5, 8)))
        val skippedWednesday = AlarmPlans.resolve(alarm, lecture, setOf(LocalDate.of(2026, 10, 7).toEpochDay()))
        assertEquals(at(14, 8, 15), AlarmTimes.nextOccurrence(skippedWednesday, at(5, 8)))
    }

    @Test
    fun blockAlarm_offsetBeforeMidnightMovesToPreviousDay() {
        val early = FixedBlock(id = 9, title = "Lab", roleId = 1, dayOfWeek = 1, startMinute = 30, endMinute = 90)
        val alarm = Alarm(id = 4, hour = 0, minute = 0, type = AlarmType.BLOCK, linkedBlockId = 9, offsetMinutesBeforeBlock = 60)
        val p = AlarmPlans.resolve(alarm, early)
        assertEquals(23 * 60 + 30, p.minuteOfDay)
        assertEquals(AlarmDays.bit(java.time.DayOfWeek.SUNDAY), p.daysMask)
    }

    @Test
    fun minutesByDate_feedsThePlanner() {
        val weekdays = plan(7, mask = AlarmDays.WEEKDAYS).copy(id = 1)
        val oneShot = plan(15).copy(id = 2)
        val off = plan(16, enabled = false).copy(id = 3)
        val map = AlarmTimes.minutesByDate(listOf(weekdays, oneShot, off), at(5, 8), days = 7)
        assertEquals(listOf(7 * 60, 15 * 60), map[LocalDate.of(2026, 10, 5)])
        assertEquals(listOf(7 * 60), map[LocalDate.of(2026, 10, 6)])
        assertNull("weekend", map[LocalDate.of(2026, 10, 10)])
    }

    @Test
    fun maskHelpers() {
        assertEquals(AlarmDays.bit(java.time.DayOfWeek.SUNDAY), AlarmDays.shiftedEarlier(AlarmDays.bit(java.time.DayOfWeek.MONDAY)))
        assertEquals(0, AlarmDays.toggle(AlarmDays.bit(java.time.DayOfWeek.FRIDAY), java.time.DayOfWeek.FRIDAY))
    }
}
