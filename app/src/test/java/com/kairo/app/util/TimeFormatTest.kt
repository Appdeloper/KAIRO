package com.kairo.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TimeFormatTest {
    @Test
    fun formatsBothClockStyles() {
        assertEquals("09:05", formatMinuteOfDay(9 * 60 + 5, use24Hour = true, locale = Locale.US))
        assertEquals("9:05 AM", formatMinuteOfDay(9 * 60 + 5, use24Hour = false, locale = Locale.US))
        assertEquals("11:30 PM", formatMinuteOfDay(23 * 60 + 30, use24Hour = false, locale = Locale.US))
        assertEquals("00:00", formatMinuteOfDay(0, use24Hour = true, locale = Locale.US))
    }
}
