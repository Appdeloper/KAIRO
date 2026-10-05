package com.kairo.app.alarm

import android.content.Context
import android.os.UserManager
import com.kairo.app.KairoApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime

fun Context.isUserUnlocked(): Boolean = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

/**
 * The one place that turns stored alarms into AlarmManager entries. Called on every alarm,
 * lecture or skip change, after boot / time / zone changes, and after each ring.
 */
object AlarmSync {
    private val mutex = Mutex()

    suspend fun syncAll(context: Context): Pair<AlarmPlan, Instant>? = mutex.withLock {
        val app = context.applicationContext
        val mirror = AlarmMirror(app)
        val scheduler = AlarmScheduler(app)
        val now = ZonedDateTime.now()
        val previous = mirror.read()
        val plans = if (app.isUserUnlocked()) loadFromRoom(app, previous, now) else previous

        mirror.write(plans)
        (previous.map { it.id }.toSet() - plans.map { it.id }.toSet()).forEach { removed ->
            scheduler.cancel(removed, AlarmScheduler.Slot.OCCURRENCE)
            scheduler.cancel(removed, AlarmScheduler.Slot.SNOOZE)
        }
        plans.forEach { schedulePlan(scheduler, it, now) }
        AlarmTimes.nextAcross(plans, now)
    }

    /** After a ring: the next occurrence is armed at once, so a crash later can't lose tomorrow's alarm. */
    fun scheduleAfterFire(context: Context, plan: AlarmPlan) {
        schedulePlan(AlarmScheduler(context.applicationContext), plan, ZonedDateTime.now())
    }

    private fun schedulePlan(scheduler: AlarmScheduler, plan: AlarmPlan, now: ZonedDateTime) {
        val ringingOneShot = plan.daysMask == AlarmDays.ONE_SHOT && RingState.current.value?.plan?.id == plan.id
        val occurrence = if (ringingOneShot) null else AlarmTimes.nextOccurrence(plan, now)
        if (occurrence != null) scheduler.schedule(plan.id, AlarmScheduler.Slot.OCCURRENCE, occurrence.toInstant()) else scheduler.cancel(plan.id, AlarmScheduler.Slot.OCCURRENCE)

        val snooze = plan.snoozedUntilMillis?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now.toInstant()) }
        if (snooze != null) scheduler.schedule(plan.id, AlarmScheduler.Slot.SNOOZE, snooze) else scheduler.cancel(plan.id, AlarmScheduler.Slot.SNOOZE)
    }

    /**
     * Room is the source of truth once unlocked. Two repairs happen here: a snooze set before the
     * first unlock (only in the mirror) is copied into Room, and a skip whose day has passed is cleared.
     */
    private suspend fun loadFromRoom(app: Context, mirrored: List<AlarmPlan>, now: ZonedDateTime): List<AlarmPlan> {
        val repo = (app as KairoApp).container.alarmRepository
        val mirroredById = mirrored.associateBy { it.id }
        return repo.plansOnce().map { plan ->
            val fromMirror = mirroredById[plan.id]
            var fixed = plan
            if (fromMirror != null && (fromMirror.snoozedUntilMillis ?: 0) > (plan.snoozedUntilMillis ?: 0)) {
                fixed = fixed.copy(snoozeCount = fromMirror.snoozeCount, snoozedUntilMillis = fromMirror.snoozedUntilMillis)
            }
            if (AlarmTimes.skipIsStale(fixed, now)) fixed = RingPolicy.unskip(fixed)
            if (fixed != plan) repo.applyRuntime(fixed)
            fixed
        }
    }
}
