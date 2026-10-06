package com.kairo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.Role
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.RoleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Settings tells the screen after a Data action. */
sealed interface DataEvent {
    data class SampleLoaded(val loaded: Boolean) : DataEvent
    data class Exported(val json: String) : DataEvent
    data object ResetDone : DataEvent
}

/** Lanes and the Data section. Profile, AI, alarms, shake and focus keep their own view models. */
class SettingsViewModel(
    private val prefs: UserPrefsRepository,
    private val roles: RoleRepository,
    private val loadSampleWeek: suspend () -> Boolean,
    private val exportData: suspend () -> String,
    private val resetAllData: suspend () -> Unit,
) : ViewModel() {
    val lanes: StateFlow<List<Role>> = roles.allRoles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hidden: StateFlow<Set<Long>> = prefs.prefs.map { it.hiddenRoleIds }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _events = Channel<DataEvent>(Channel.BUFFERED)
    val events: Flow<DataEvent> = _events.receiveAsFlow()

    fun rename(role: Role, name: String) = viewModelScope.launch { roles.rename(role.id, name) }
    fun setHidden(role: Role, hidden: Boolean) = viewModelScope.launch { prefs.setRoleHidden(role.id, hidden) }
    fun loadSample() = viewModelScope.launch { _events.send(DataEvent.SampleLoaded(loadSampleWeek())) }
    fun export() = viewModelScope.launch { _events.send(DataEvent.Exported(exportData())) }

    fun reset() = viewModelScope.launch {
        resetAllData()
        _events.send(DataEvent.ResetDone)
    }
}
