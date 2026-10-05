package com.kairo.app.ui.shake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.prefs.ShakePrefsRepository
import com.kairo.app.data.prefs.ShakeSettings
import com.kairo.app.service.shake.ShakeService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShakeUiState(val settings: ShakeSettings = ShakeSettings(), val running: Boolean = false, val loaded: Boolean = false)

/** Settings only; starting/stopping the service needs a visible Context, so the screen does that. */
class ShakeViewModel(private val prefs: ShakePrefsRepository) : ViewModel() {
    val state: StateFlow<ShakeUiState> = combine(prefs.settings, ShakeService.running) { s, running -> ShakeUiState(s, running, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShakeUiState())

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setEnabled(enabled) }
    fun setThreshold(value: Float) = viewModelScope.launch { prefs.setThreshold(value) }
    fun setOnlyWhileCharging(value: Boolean) = viewModelScope.launch { prefs.setOnlyWhileCharging(value) }
    fun setActiveHours(start: Int, end: Int) = viewModelScope.launch { prefs.setActiveHours(start, end) }
}
