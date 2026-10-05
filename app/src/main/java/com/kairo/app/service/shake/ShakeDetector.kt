package com.kairo.app.service.shake

import kotlin.math.sqrt

/**
 * Pure shake detection, fed raw accelerometer samples (m/s²) with timestamps in ms.
 *
 * A low-pass filter tracks gravity; subtracting it leaves the user's own movement, whose magnitude
 * is compared to [threshold]. One "shake" is a rising edge above the threshold; the gesture fires
 * on the second shake within [windowMs], then ignores everything for [debounceMs].
 */
class ShakeDetector(
    var threshold: Float = DEFAULT_THRESHOLD,
    private val windowMs: Long = WINDOW_MS,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val alpha: Float = LOW_PASS_ALPHA,
) {
    private val gravity = FloatArray(3)
    private var initialised = false
    private var armed = true
    private var firstShakeAt: Long? = null
    private var lastTriggerAt: Long? = null

    /** Latest movement magnitude (gravity removed), for the live meter in Settings. */
    var lastMagnitude: Float = 0f
        private set

    /** Returns true exactly when a two-shake gesture completes. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        lastMagnitude = movementMagnitude(x, y, z)
        if (lastTriggerAt?.let { timeMs - it < debounceMs } == true) return false

        // Hysteresis: a shake must drop back well below the threshold before the next one counts,
        // so one long jolt isn't read as two shakes.
        if (!armed) {
            if (lastMagnitude < threshold * REARM_FRACTION) armed = true
            return false
        }
        if (lastMagnitude < threshold) return false
        armed = false

        val first = firstShakeAt
        return if (first != null && timeMs - first <= windowMs) {
            firstShakeAt = null
            lastTriggerAt = timeMs
            true
        } else {
            firstShakeAt = timeMs
            false
        }
    }

    private fun movementMagnitude(x: Float, y: Float, z: Float): Float {
        if (!initialised) {
            // Start from the first reading so registering the sensor never looks like a shake.
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            initialised = true
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z
        }
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        return sqrt(lx * lx + ly * ly + lz * lz)
    }

    companion object {
        const val WINDOW_MS = 600L
        const val DEBOUNCE_MS = 2_000L
        const val LOW_PASS_ALPHA = 0.8f
        const val REARM_FRACTION = 0.6f

        /** m/s² of movement on top of gravity. Low = sensitive. The Settings slider maps onto this range. */
        const val MIN_THRESHOLD = 8f
        const val MAX_THRESHOLD = 30f
        const val DEFAULT_THRESHOLD = 14f
    }
}
