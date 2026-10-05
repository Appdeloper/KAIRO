package com.kairo.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.Role
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.RoleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by onboarding and Settings: both edit the same three profile fields. */
class ProfileViewModel(
    private val prefsRepository: UserPrefsRepository,
    roleRepository: RoleRepository,
) : ViewModel() {

    /** Null until DataStore has been read once, so the UI can avoid flashing defaults. */
    val prefs: StateFlow<UserPrefs?> = prefsRepository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roles: StateFlow<List<Role>> = roleRepository.allRoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(firstName: String, wakeMinute: Int, sleepMinute: Int) {
        if (firstName.isBlank()) return
        viewModelScope.launch { prefsRepository.saveProfile(firstName, wakeMinute, sleepMinute) }
    }
}
