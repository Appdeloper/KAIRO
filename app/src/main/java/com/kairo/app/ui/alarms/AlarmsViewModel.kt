package com.kairo.app.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmPlan
import com.kairo.app.alarm.AlarmPlans.withRuntimeFrom
import com.kairo.app.alarm.AlarmTimes
import com.kairo.app.alarm.RingPolicy
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.AlarmRepository
import com.kairo.app.data.repository.TimetableRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

data class AlarmRowUi(val alarm: Alarm, val plan: AlarmPlan, val nextRing: Instant?)

data class AlarmsUiState(
    val rows: List<AlarmRowUi> = emptyList(),
    val blocks: List<FixedBlock> = emptyList(),
    val wakeMinute: Int = 7 * 60,
    val next: Pair<AlarmPlan, Instant>? = null,
    val loaded: Boolean = false,
)

/** Re-emits every 30 s so "next alarm" stays correct while the screen is open. */
private val ticker = flow {
    while (true) {
        emit(Unit)
        delay(30_000)
    }
}

/**
 * Alarm CRUD. Writes go to Room only; KairoApp observes Room and re-syncs AlarmManager, so every
 * writer (this screen, the ring service, timetable edits) schedules alarms the same way.
 */
class AlarmsViewModel(
    private val repository: AlarmRepository,
    timetable: TimetableRepository,
    prefs: UserPrefsRepository,
) : ViewModel() {

    val state: StateFlow<AlarmsUiState> = combine(repository.alarms(), repository.plans(), timetable.allBlocks(), prefs.prefs, ticker) { alarms, plans, blocks, profile, _ ->
        val now = ZonedDateTime.now()
        val plansById = plans.associateBy { it.id }
        AlarmsUiState(
            rows = alarms.mapNotNull { a -> plansById[a.id]?.let { AlarmRowUi(a, it, AlarmTimes.nextTrigger(it, now)) } },
            blocks = blocks,
            wakeMinute = profile.wakeMinute,
            next = AlarmTimes.nextAcross(plans, now),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmsUiState())

    fun save(alarm: Alarm) = launchWrite { repository.save(alarm) }
    fun delete(alarm: Alarm) = launchWrite { repository.delete(alarm) }

    fun setEnabled(alarm: Alarm, enabled: Boolean) =
        launchWrite { repository.save(alarm.copy(enabled = enabled, snoozeCount = 0, snoozedUntilMillis = null)) }

    fun toggleDay(alarm: Alarm, day: java.time.DayOfWeek) =
        launchWrite { repository.save(alarm.copy(daysOfWeekMask = AlarmDays.toggle(alarm.daysOfWeekMask, day))) }

    fun toggleSkipNext(row: AlarmRowUi) = launchWrite {
        val plan = if (row.plan.skipNextOnce) RingPolicy.unskip(row.plan) else RingPolicy.skipNext(row.plan, ZonedDateTime.now())
        repository.save(row.alarm.withRuntimeFrom(plan))
    }

    /** Only ever called from an explicit user confirmation (onboarding checkbox or the empty-state card). */
    fun createWeekdayWakeAlarm(wakeMinute: Int, label: String) = launchWrite {
        repository.save(Alarm(label = label, hour = wakeMinute / 60, minute = wakeMinute % 60, daysOfWeekMask = AlarmDays.WEEKDAYS, type = AlarmType.WAKE))
    }

    /** One-shot test alarm at the next whole minute at least 60 s away, so it's "in about a minute". */
    fun scheduleTestAlarm(label: String) = launchWrite {
        val at = ZonedDateTime.now().plusSeconds(TEST_LEAD_SECONDS).let { t -> if (t.second == 0) t else t.plusMinutes(1).withSecond(0) }
        // Reuse one test alarm instead of piling up a new row per tap.
        val existing = repository.alarms().first().firstOrNull { it.type == AlarmType.ONE_SHOT && it.label == label }
        val fresh = Alarm(label = label, hour = at.hour, minute = at.minute, type = AlarmType.ONE_SHOT, rampUpSeconds = TEST_RAMP_SECONDS)
        repository.save(existing?.copy(hour = at.hour, minute = at.minute, enabled = true, snoozeCount = 0, snoozedUntilMillis = null) ?: fresh)
    }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val TEST_LEAD_SECONDS = 60L
        private const val TEST_RAMP_SECONDS = 5
    }
}
