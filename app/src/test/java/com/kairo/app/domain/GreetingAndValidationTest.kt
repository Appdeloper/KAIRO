package com.kairo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GreetingAndValidationTest {
    @Test
    fun dayPartBoundaries() {
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(4 * 60 + 59))
        assertEquals(DayPart.MORNING, Greeting.dayPartFor(5 * 60))
        assertEquals(DayPart.MORNING, Greeting.dayPartFor(11 * 60 + 59))
        assertEquals(DayPart.AFTERNOON, Greeting.dayPartFor(12 * 60))
        assertEquals(DayPart.EVENING, Greeting.dayPartFor(17 * 60))
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(22 * 60))
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(0))
    }

    @Test
    fun blockValidation() {
        assertNull(BlockValidation.validate("DBMS", 1, 540, 600))
        assertEquals(BlockError.EMPTY_TITLE, BlockValidation.validate("  ", 1, 540, 600))
        assertEquals(BlockError.END_NOT_AFTER_START, BlockValidation.validate("DBMS", 1, 600, 600))
        assertEquals(BlockError.END_NOT_AFTER_START, BlockValidation.validate("DBMS", 1, 600, 540))
        assertEquals(BlockError.BAD_DAY, BlockValidation.validate("DBMS", 0, 540, 600))
        assertEquals(BlockError.BAD_DAY, BlockValidation.validate("DBMS", 8, 540, 600))
    }
}
