package com.kairo.app.service.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic accelerometer streams at SENSOR_DELAY_UI (~60 ms per sample). */
class ShakeDetectorTest {
    private val g = 9.81f
    private val step = 60L

    /** Feeds [samples] (x-axis jolts on top of gravity on z) and returns the times the gesture fired. */
    private fun run(detector: ShakeDetector, samples: List<Float>, start: Long = 0L): List<Long> =
        samples.mapIndexedNotNull { i, x -> (start + i * step).takeIf { detector.onSample(x, 0f, g, it) } }

    private fun still(n: Int) = List(n) { 0f }
    private fun jolt(strength: Float = 25f) = listOf(strength)

    @Test
    fun singleShakeIsIgnored() {
        assertTrue(run(ShakeDetector(), still(10) + jolt() + still(40)).isEmpty())
    }

    @Test
    fun twoShakesWithin600msTrigger() {
        // Jolts 300 ms apart.
        val fired = run(ShakeDetector(), still(10) + jolt() + still(4) + jolt() + still(10))
        assertEquals(1, fired.size)
    }

    @Test
    fun twoShakesMoreThan600msApartAreIgnored() {
        // Jolts 720 ms apart: the first expires, the second just starts a new pair.
        assertTrue(run(ShakeDetector(), still(10) + jolt() + still(11) + jolt() + still(20)).isEmpty())
    }

    @Test
    fun debounceSwallowsShakesForTwoSeconds() {
        val pair = jolt() + still(4) + jolt()
        // Second pair starts ~1.2 s after the first fires: ignored. Third pair ~2.6 s later: fires.
        val fired = run(ShakeDetector(), still(10) + pair + still(14) + pair + still(25) + pair + still(5))
        assertEquals(2, fired.size)
        assertTrue(fired[1] - fired[0] >= ShakeDetector.DEBOUNCE_MS)
    }

    @Test
    fun thresholdChangesTakeEffect() {
        // The gravity filter absorbs ~20% of a jolt: 12 m/s² reads as ~9.6, then ~8.8 for the second.
        val gentle = still(10) + jolt(12f) + still(4) + jolt(12f) + still(10)
        assertTrue("below the default 14", run(ShakeDetector(), gentle).isEmpty())
        val sensitive = ShakeDetector(threshold = 8f)
        assertEquals(1, run(sensitive, gentle).size)
        val detector = ShakeDetector()
        detector.threshold = 8f
        assertEquals(1, run(detector, gentle).size)
    }

    @Test
    fun noiseBelowThresholdNeverTriggers() {
        val noise = List(500) { i -> if (i % 2 == 0) 3f else -3f }
        assertTrue(run(ShakeDetector(), noise).isEmpty())
    }

    @Test
    fun oneLongJoltIsNotTwoShakes() {
        // Stays above threshold for 4 samples: only one rising edge.
        assertTrue(run(ShakeDetector(), still(10) + List(4) { 25f } + still(20)).isEmpty())
    }

    @Test
    fun firstReadingIsNotASpike() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(0f, 0f, g, 0))
        assertEquals(0f, detector.lastMagnitude, 0.001f)
    }
}
