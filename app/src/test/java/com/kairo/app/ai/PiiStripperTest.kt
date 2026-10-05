package com.kairo.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiStripperTest {
    @Test
    fun stripsEmailsAndPhones() {
        assertEquals("mail [email] now", PiiStripper.strip("mail rohan.k@gmail.com now"))
        assertEquals("call [phone] at 6", PiiStripper.strip("call +91 98765 43210 at 6"))
        assertEquals("call [phone] kal", PiiStripper.strip("call 9876543210 kal"))
        assertEquals("office [phone]", PiiStripper.strip("office 022-2654-1234"))
    }

    @Test
    fun keepsTimesDurationsShortNumbersAndDates() {
        val text = "reel 12 edit 18:30 pe, 2 ghante, submit by 2026-10-07, room 204"
        assertEquals(text, PiiStripper.strip(text))
    }
}
