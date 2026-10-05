package com.kairo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.ai.AiSettings
import com.kairo.app.data.prefs.AiSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSettingsViewModel(private val repository: AiSettingsRepository) : ViewModel() {

    /** Null until DataStore is read, so the fields never flash empty over saved values. */
    val settings: StateFlow<AiSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(settings: AiSettings) {
        viewModelScope.launch { repository.save(settings) }
    }
}
