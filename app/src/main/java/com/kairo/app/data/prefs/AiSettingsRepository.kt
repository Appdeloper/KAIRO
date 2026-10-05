package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kairo.app.ai.AiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Lives in the same DataStore file as the profile; keys are prefixed to keep them apart. */
class AiSettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AiSettings> = store.data.map { p ->
        AiSettings(
            enabled = p[ENABLED] ?: true,
            baseUrl = p[BASE_URL].orEmpty(),
            deviceToken = p[DEVICE_TOKEN].orEmpty(),
        )
    }

    suspend fun current(): AiSettings = settings.first()

    suspend fun save(settings: AiSettings) {
        store.edit {
            it[ENABLED] = settings.enabled
            it[BASE_URL] = settings.baseUrl.trim()
            it[DEVICE_TOKEN] = settings.deviceToken.trim()
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("ai_enabled")
        val BASE_URL = stringPreferencesKey("ai_base_url")
        val DEVICE_TOKEN = stringPreferencesKey("ai_device_token")
    }
}
