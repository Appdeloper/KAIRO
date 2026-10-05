package com.kairo.app.alarm

import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.domain.plan.MINUTES_PER_DAY
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** Weekday bit masks: bit 0 = Monday … bit 6 = Sunday (ISO order, like FixedBlock.dayOfWeek). */
object AlarmDays {
    const val ONE_SHOT = 0
    const val WEEKDAYS = 0b0011111
    const val EVERY_DAY = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    /** Moves every day bit one day earlier (Monday wraps to Sunday). */
    fun shiftedEarlier(mask: Int): Int = DayOfWeek.entries.filter { contains(mask, it) }.fold(0) { acc, d -> acc or bit(d.minus(1)) }
}

/**
 * Everything needed to schedule and ring one alarm, with block links already resolved to a time.
 * Serializable because a copy lives in device-protected storage so alarms can ring after a reboot
 * before the user unlocks (when Room's encrypted storage is unavailable).
 */
@Serializable
data class AlarmPlan(
    val id: Long,
    val label: String,
    val type: AlarmType,
    val minuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val rampUpSeconds: Int = 20,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val skipNextOnce: Boolean = false,
    val skipDateEpochDay: Long? = null,
    /** Days the linked lecture is skipped (one-day exceptions), so its alarm stays quiet too. */
    val skipEpochDays: Set<Long> = emptySet(),
    val openBriefingOnDismiss: Boolean = true,
    val snoozeCount: Int = 0,
    val snoozedUntilMillis: Long? = null,
)

object AlarmPlans {
    /**
     * A BLOCK alarm follows its lecture: weekday from the block, time = block start minus offset.
     * If the offset reaches back past midnight, the alarm moves to the previous day.
     */
    fun resolve(alarm: Alarm, block: FixedBlock?, blockSkipEpochDays: Set<Long> = emptySet()): AlarmPlan {
        var minute = alarm.hour * 60 + alarm.minute
        var mask = alarm.daysOfWeekMask
        var skips = emptySet<Long>()
        if (alarm.type == AlarmType.BLOCK && block != null) {
            val raw = block.startMinute - (alarm.offsetMinutesBeforeBlock ?: 0)
            val wrapsToPreviousDay = raw < 0
            minute = Math.floorMod(raw, MINUTES_PER_DAY)
            mask = AlarmDays.bit(DayOfWeek.of(block.dayOfWeek)).let { if (wrapsToPreviousDay) AlarmDays.shiftedEarlier(it) else it }
            skips = if (wrapsToPreviousDay) blockSkipEpochDays.map { it - 1 }.toSet() else blockSkipEpochDays
        }
        return AlarmPlan(
            id = alarm.id,
            label = alarm.label,
            type = alarm.type,
            minuteOfDay = minute,
            daysMask = mask,
            enabled = alarm.enabled,
            ringtoneUri = alarm.ringtoneUri,
            vibrate = alarm.vibrate,
            rampUpSeconds = alarm.rampUpSeconds.coerceAtLeast(0),
            snoozeMinutes = alarm.snoozeMinutes.coerceAtLeast(1),
            maxSnoozes = alarm.maxSnoozes.coerceAtLeast(0),
            skipNextOnce = alarm.skipNextOnce,
            skipDateEpochDay = alarm.skipDateEpochDay,
            skipEpochDays = skips,
            openBriefingOnDismiss = alarm.openBriefingOnDismiss,
            snoozeCount = alarm.snoozeCount,
            snoozedUntilMillis = alarm.snoozedUntilMillis,
        )
    }

    /** Copies ring-time state (snooze, enabled, skip) from a plan back onto the stored alarm. */
    fun Alarm.withRuntimeFrom(plan: AlarmPlan): Alarm = copy(
        enabled = plan.enabled,
        skipNextOnce = plan.skipNextOnce,
        skipDateEpochDay = plan.skipDateEpochDay,
        snoozeCount = plan.snoozeCount,
        snoozedUntilMillis = plan.snoozedUntilMillis,
    )
}
