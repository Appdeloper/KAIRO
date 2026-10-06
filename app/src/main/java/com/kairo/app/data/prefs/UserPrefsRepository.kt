package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPrefs(
    val firstName: String = "",
    /** Minutes from local midnight. Defaults are only placeholders until onboarding finishes. */
    val wakeMinute: Int = DEFAULT_WAKE_MINUTE,
    val sleepMinute: Int = DEFAULT_SLEEP_MINUTE,
    val onboardingDone: Boolean = false,
    /** Lanes the user turned off: hidden from pickers and filters, never deleted (their history stays). */
    val hiddenRoleIds: Set<Long> = emptySet(),
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
            hiddenRoleIds = p[HIDDEN_ROLES].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
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

    /** Set once the sample week is in, so a second tap doesn't duplicate it. Cleared by a full reset. */
    val sampleWeekLoaded: Flow<Boolean> = store.data.map { it[SAMPLE_LOADED] ?: false }

    suspend fun markSampleWeekLoaded() {
        store.edit { it[SAMPLE_LOADED] = true }
    }

    suspend fun setRoleHidden(roleId: Long, hidden: Boolean) {
        store.edit {
            val current = it[HIDDEN_ROLES].orEmpty()
            it[HIDDEN_ROLES] = if (hidden) current + roleId.toString() else current - roleId.toString()
        }
    }

    /** Spoken briefing on/off. Separate flow so toggling it doesn't re-emit the whole profile. */
    val speechMuted: Flow<Boolean> = store.data.map { it[SPEECH_MUTED] ?: false }

    suspend fun setSpeechMuted(muted: Boolean) {
        store.edit { it[SPEECH_MUTED] = muted }
    }

    private companion object {
        val SPEECH_MUTED = booleanPreferencesKey("speech_muted")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val WAKE_MINUTE = intPreferencesKey("wake_minute")
        val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val HIDDEN_ROLES = stringSetPreferencesKey("hidden_role_ids")
        val SAMPLE_LOADED = booleanPreferencesKey("sample_week_loaded")
    }
}
