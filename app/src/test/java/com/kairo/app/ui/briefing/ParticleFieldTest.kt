package com.kairo.app.ui.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleFieldTest {
    @Test
    fun burstsAreCappedAt120() {
        assertEquals(120, ParticleField.spawn(10_000, seed = 1).size)
        assertEquals(ParticleField.DEFAULT_COUNT, ParticleField.spawn(ParticleField.DEFAULT_COUNT, seed = 1).size)
        assertEquals(0, ParticleField.spawn(-5, seed = 1).size)
    }

    @Test
    fun sameSeedSameBurst_andColoursStayInPalette() {
        assertEquals(ParticleField.spawn(50, seed = 7), ParticleField.spawn(50, seed = 7))
        assertTrue(ParticleField.spawn(120, seed = 3).all { it.colorIndex in 0 until ParticleField.PALETTE_SIZE })
    }
}
