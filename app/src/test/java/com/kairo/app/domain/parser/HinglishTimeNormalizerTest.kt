package com.kairo.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HinglishTimeNormalizerTest {
    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun fractionalHinglishTimes() {
        assertEquals(ClockTime(6, 30), HinglishTimeNormalizer.parse("saade chhe"))
        assertEquals(ClockTime(6, 45), HinglishTimeNormalizer.parse("pone saat"))
        assertEquals(ClockTime(6, 45), HinglishTimeNormalizer.parse("paune saat baje"))
        assertEquals(ClockTime(5, 15), HinglishTimeNormalizer.parse("sava paanch"))
        assertEquals(ClockTime(1, 30), HinglishTimeNormalizer.parse("dedh"))
        assertEquals(ClockTime(2, 30), HinglishTimeNormalizer.parse("dhai"))
        assertEquals(ClockTime(2, 30), HinglishTimeNormalizer.parse("dhai baje"))
        assertEquals(ClockTime(12, 45), HinglishTimeNormalizer.parse("paune ek"))
        assertEquals(ClockTime(10, 30), HinglishTimeNormalizer.parse("saade 10"))
    }

    @Test
    fun plainAndEnglishTimes() {
        assertEquals(ClockTime(5, 0), HinglishTimeNormalizer.parse("5 baje"))
        assertEquals(ClockTime(8, 0), HinglishTimeNormalizer.parse("aath baje"))
        assertEquals(ClockTime(6, 0, Meridiem.PM), HinglishTimeNormalizer.parse("6pm"))
        assertEquals(ClockTime(6, 30, Meridiem.AM), HinglishTimeNormalizer.parse("6:30 am"))
        assertEquals(ClockTime(18, 15), HinglishTimeNormalizer.parse("18:15"))
        assertNull(HinglishTimeNormalizer.parse("gym"))
        assertNull("bare number words aren't times", HinglishTimeNormalizer.parse("do"))
        assertNull(HinglishTimeNormalizer.parse("13pm"))
    }

    @Test
    fun dayHintsActAsAmPm() {
        assertEquals(h(7), HinglishTimeNormalizer.withHint(ClockTime(7, 0), DayHint.MORNING))
        assertEquals(h(14), HinglishTimeNormalizer.withHint(ClockTime(2, 0), DayHint.AFTERNOON))
        assertEquals(h(12), HinglishTimeNormalizer.withHint(ClockTime(12, 0), DayHint.AFTERNOON))
        assertEquals(h(18, 30), HinglishTimeNormalizer.withHint(ClockTime(6, 30), DayHint.EVENING))
        assertEquals(h(22), HinglishTimeNormalizer.withHint(ClockTime(10, 0), DayHint.NIGHT))
        assertEquals(h(0), HinglishTimeNormalizer.withHint(ClockTime(12, 0), DayHint.NIGHT))
        assertEquals(h(2), HinglishTimeNormalizer.withHint(ClockTime(2, 0), DayHint.NIGHT))
    }

    @Test
    fun ambiguousHourPicksNextSensibleReading() {
        fun resolve(hour: Int, isToday: Boolean, now: Int) =
            HinglishTimeNormalizer.toMinuteOfDay(ClockTime(hour, 0), null, isToday, now, wakeMinute = h(7), dayEndMinute = h(23, 30))
        assertEquals("6 at 9am today -> evening", h(18), resolve(6, isToday = true, now = h(9)))
        assertEquals("10 at 8am today -> morning", h(10), resolve(10, isToday = true, now = h(8)))
        assertEquals("5 tomorrow -> before wake, so evening", h(17), resolve(5, isToday = false, now = h(9)))
        assertEquals("9 tomorrow -> morning", h(9), resolve(9, isToday = false, now = h(20)))
        assertEquals("12 -> noon", h(12), resolve(12, isToday = false, now = h(9)))
    }
}
