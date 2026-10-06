package com.kairo.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.Role
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.RoleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lanes, renames and the finish step of onboarding. The profile itself is saved last, which ends onboarding. */
class OnboardingViewModel(
    private val prefs: UserPrefsRepository,
    private val roles: RoleRepository,
    private val loadSampleWeek: suspend () -> Boolean,
    private val createWakeAlarm: suspend (wakeMinute: Int, label: String) -> Unit,
) : ViewModel() {

    val lanes: StateFlow<List<Role>> = roles.allRoles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hidden: StateFlow<Set<Long>> = prefs.prefs.map { it.hiddenRoleIds }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun rename(role: Role, name: String) = viewModelScope.launch { roles.rename(role.id, name) }
    fun setHidden(role: Role, hidden: Boolean) = viewModelScope.launch { prefs.setRoleHidden(role.id, hidden) }

    /** Order matters: data first, profile last, because saving the profile swaps onboarding for the app. */
    fun finish(name: String, wakeMinute: Int, sleepMinute: Int, addWakeAlarm: Boolean, sampleWeek: Boolean, wakeLabel: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (sampleWeek) loadSampleWeek()
            if (addWakeAlarm) createWakeAlarm(wakeMinute, wakeLabel)
            prefs.saveProfile(name, wakeMinute, sleepMinute)
        }
    }
}
