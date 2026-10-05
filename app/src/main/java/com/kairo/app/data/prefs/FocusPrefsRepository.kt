package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.kairo.app.domain.focus.FocusDurations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class FocusSettings(
    val defaultMinutes: Int = FocusDurations.DEFAULT_MINUTES,
    /** User opted in to "Silence notifications during focus". Off by default. */
    val silenceDuringFocus: Boolean = false,
    /** We switched DND on and still owe a restore; survives process death. */
    val dndApplied: Boolean = false,
    /** The filter before we changed it (API < 35 only; 35+ uses the app's own zen rule). */
    val dndPreviousFilter: Int? = null,
    /** The filter we set, so we only restore if the user hasn't changed it since. */
    val dndAppliedFilter: Int? = null,
    /** Session whose Live Update the user swiped away; we must not re-post it. */
    val dismissedSessionId: Long? = null,
)

class FocusPrefsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<FocusSettings> = store.data.map { p ->
        FocusSettings(
            defaultMinutes = p[DEFAULT_MINUTES] ?: FocusDurations.DEFAULT_MINUTES,
            silenceDuringFocus = p[SILENCE] ?: false,
            dndApplied = p[DND_APPLIED] ?: false,
            dndPreviousFilter = p[DND_PREVIOUS],
            dndAppliedFilter = p[DND_APPLIED_FILTER],
            dismissedSessionId = p[DISMISSED],
        )
    }

    suspend fun current(): FocusSettings = settings.first()

    suspend fun setDefaultMinutes(minutes: Int) = store.edit { it[DEFAULT_MINUTES] = minutes }
    suspend fun setSilenceDuringFocus(on: Boolean) = store.edit { it[SILENCE] = on }

    suspend fun recordDndApplied(previousFilter: Int?, appliedFilter: Int) = store.edit {
        it[DND_APPLIED] = true
        if (previousFilter != null) it[DND_PREVIOUS] = previousFilter else it.remove(DND_PREVIOUS)
        it[DND_APPLIED_FILTER] = appliedFilter
    }

    suspend fun clearDndApplied() = store.edit {
        it.remove(DND_APPLIED)
        it.remove(DND_PREVIOUS)
        it.remove(DND_APPLIED_FILTER)
    }

    suspend fun markDismissed(sessionId: Long) = store.edit { it[DISMISSED] = sessionId }
    suspend fun clearDismissed() = store.edit { it.remove(DISMISSED) }

    private companion object {
        val DEFAULT_MINUTES = intPreferencesKey("focus_default_minutes")
        val SILENCE = booleanPreferencesKey("focus_silence")
        val DND_APPLIED = booleanPreferencesKey("focus_dnd_applied")
        val DND_PREVIOUS = intPreferencesKey("focus_dnd_previous")
        val DND_APPLIED_FILTER = intPreferencesKey("focus_dnd_applied_filter")
        val DISMISSED = longPreferencesKey("focus_dismissed_session")
    }
}
