package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPrefs(
    val firstName: String = "",
    /** Minutes from local midnight. Defaults are only placeholders until onboarding finishes. */
    val wakeMinute: Int = DEFAULT_WAKE_MINUTE,
    val sleepMinute: Int = DEFAULT_SLEEP_MINUTE,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_WAKE_MINUTE = 7 * 60
        const val DEFAULT_SLEEP_MINUTE = 23 * 60 + 30
    }
}

class UserPrefsRepository(private val store: DataStore<Preferences>) {

    val prefs: Flow<UserPrefs> = store.data.map { p ->
        UserPrefs(
            firstName = p[FIRST_NAME].orEmpty(),
            wakeMinute = p[WAKE_MINUTE] ?: UserPrefs.DEFAULT_WAKE_MINUTE,
            sleepMinute = p[SLEEP_MINUTE] ?: UserPrefs.DEFAULT_SLEEP_MINUTE,
            onboardingDone = p[ONBOARDING_DONE] ?: false,
        )
    }

    /** Written atomically so a half-finished onboarding never flags itself as done. */
    suspend fun saveProfile(firstName: String, wakeMinute: Int, sleepMinute: Int) {
        store.edit {
            it[FIRST_NAME] = firstName.trim()
            it[WAKE_MINUTE] = wakeMinute
            it[SLEEP_MINUTE] = sleepMinute
            it[ONBOARDING_DONE] = true
        }
    }

    private companion object {
        val FIRST_NAME = stringPreferencesKey("first_name")
        val WAKE_MINUTE = intPreferencesKey("wake_minute")
        val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
