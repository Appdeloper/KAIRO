package com.kairo.app.service.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakePolicyTest {
    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun activeHours_defaultDay() {
        assertFalse(ActiveHours.isActive(h(6, 59), ActiveHours.DEFAULT_START, ActiveHours.DEFAULT_END))
        assertTrue(ActiveHours.isActive(h(7), ActiveHours.DEFAULT_START, ActiveHours.DEFAULT_END))
        assertTrue(ActiveHours.isActive(h(22, 59), ActiveHours.DEFAULT_START, ActiveHours.DEFAULT_END))
        assertFalse(ActiveHours.isActive(h(23), ActiveHours.DEFAULT_START, ActiveHours.DEFAULT_END))
    }

    @Test
    fun activeHours_wrapPastMidnightAndAlwaysOn() {
        assertTrue(ActiveHours.isActive(h(23, 30), h(22), h(2)))
        assertTrue(ActiveHours.isActive(h(1), h(22), h(2)))
        assertFalse(ActiveHours.isActive(h(12), h(22), h(2)))
        assertTrue(ActiveHours.isActive(h(4), h(9), h(9)))
    }

    @Test
    fun heartbeatStaleness() {
        val now = 10_000_000L
        assertEquals(ShakeStatus.OFF, ShakeHealthPolicy.status(false, runningNow = true, lastHeartbeatMillis = now, nowMillis = now))
        assertEquals(ShakeStatus.ARMED, ShakeHealthPolicy.status(true, runningNow = true, lastHeartbeatMillis = null, nowMillis = now))
        assertEquals(ShakeStatus.ARMED, ShakeHealthPolicy.status(true, false, now - 2 * 60_000, now))
        assertEquals(ShakeStatus.ARMED, ShakeHealthPolicy.status(true, false, now - ShakeHealthPolicy.STALE_AFTER_MS, now))
        assertEquals(ShakeStatus.STOPPED, ShakeHealthPolicy.status(true, false, now - ShakeHealthPolicy.STALE_AFTER_MS - 1, now))
        assertEquals(ShakeStatus.STOPPED, ShakeHealthPolicy.status(true, false, null, now))
    }

    @Test
    fun restartsCountPerDay() {
        assertEquals(100L to 1, RestartCounter.increment(null, 0, 100))
        assertEquals(100L to 3, RestartCounter.increment(100, 2, 100))
        assertEquals(101L to 1, RestartCounter.increment(100, 7, 101))
        assertEquals(0, RestartCounter.countToday(100, 7, 101))
        assertEquals(7, RestartCounter.countToday(101, 7, 101))
    }

    @Test
    fun oemGuideLookup() {
        listOf("Xiaomi", "redmi", " POCO ").forEach { assertEquals(it, OemFamily.XIAOMI, OemGuides.forDevice(it)?.family) }
        assertEquals(OemFamily.VIVO, OemGuides.forDevice("vivo")?.family)
        assertEquals(OemFamily.VIVO, OemGuides.forDevice("iQOO")?.family)
        assertEquals(OemFamily.OPPO, OemGuides.forDevice("realme")?.family)
        assertEquals(OemFamily.ONEPLUS, OemGuides.forDevice("OnePlus")?.family)
        assertEquals(OemFamily.SAMSUNG, OemGuides.forDevice("samsung")?.family)
        // Sub-brands that report the parent manufacturer but their own brand.
        assertEquals(OemFamily.XIAOMI, OemGuides.forDevice("unknown", brand = "Redmi")?.family)
        assertNull(OemGuides.forDevice("Google"))
        assertTrue(OemGuides.forDevice("oppo")!!.targets.isNotEmpty())
    }
}
