package com.kairo.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The alarm ringing right now, shared by AlarmRingService (writer) and AlarmRingActivity (reader). */
object RingState {
    data class Ringing(val plan: AlarmPlan, val startedAtMillis: Long)

    private val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current.asStateFlow()

    internal fun set(ringing: Ringing?) {
        _current.value = ringing
    }
}
