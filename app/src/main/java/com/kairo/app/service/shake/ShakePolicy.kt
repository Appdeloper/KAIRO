package com.kairo.app.service.shake

/** Minutes after midnight. End may be earlier than start (e.g. 22:00-02:00 wraps past midnight). */
object ActiveHours {
    const val DEFAULT_START = 7 * 60
    const val DEFAULT_END = 23 * 60

    fun isActive(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> true // a zero-length window would make shake useless; treat it as "always"
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }
}

enum class ShakeStatus { OFF, ARMED, STOPPED }

/**
 * Honest health: only "armed" if the service is running in this process right now, or proved
 * itself alive recently. Anything else while enabled means the phone stopped it.
 */
object ShakeHealthPolicy {
    const val STALE_AFTER_MS = 3 * 60 * 1000L

    fun status(enabled: Boolean, runningNow: Boolean, lastHeartbeatMillis: Long?, nowMillis: Long): ShakeStatus = when {
        !enabled -> ShakeStatus.OFF
        runningNow -> ShakeStatus.ARMED
        lastHeartbeatMillis != null && nowMillis - lastHeartbeatMillis <= STALE_AFTER_MS -> ShakeStatus.ARMED
        else -> ShakeStatus.STOPPED
    }
}

/** Restarts counted per local day; a new day starts the count again. */
object RestartCounter {
    fun increment(storedEpochDay: Long?, storedCount: Int, todayEpochDay: Long): Pair<Long, Int> =
        todayEpochDay to if (storedEpochDay == todayEpochDay) storedCount + 1 else 1

    fun countToday(storedEpochDay: Long?, storedCount: Int, todayEpochDay: Long): Int =
        if (storedEpochDay == todayEpochDay) storedCount else 0
}
