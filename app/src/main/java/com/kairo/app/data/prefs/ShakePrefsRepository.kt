package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kairo.app.service.shake.ActiveHours
import com.kairo.app.service.shake.RestartCounter
import com.kairo.app.service.shake.ShakeDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ShakeSettings(
    val enabled: Boolean = false,
    val threshold: Float = ShakeDetector.DEFAULT_THRESHOLD,
    val activeStartMinute: Int = ActiveHours.DEFAULT_START,
    val activeEndMinute: Int = ActiveHours.DEFAULT_END,
    val onlyWhileCharging: Boolean = false,
    val lastHeartbeatMillis: Long? = null,
    val restartEpochDay: Long? = null,
    val restartCount: Int = 0,
    val lastLaunchPath: String? = null,
)

/** Shake settings plus its health trail (heartbeat, restarts, last launch path), in the app DataStore. */
class ShakePrefsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<ShakeSettings> = store.data.map { p ->
        ShakeSettings(
            enabled = p[ENABLED] ?: false,
            threshold = p[THRESHOLD] ?: ShakeDetector.DEFAULT_THRESHOLD,
            activeStartMinute = p[ACTIVE_START] ?: ActiveHours.DEFAULT_START,
            activeEndMinute = p[ACTIVE_END] ?: ActiveHours.DEFAULT_END,
            onlyWhileCharging = p[ONLY_CHARGING] ?: false,
            lastHeartbeatMillis = p[HEARTBEAT],
            restartEpochDay = p[RESTART_DAY],
            restartCount = p[RESTART_COUNT] ?: 0,
            lastLaunchPath = p[LAST_PATH],
        )
    }

    suspend fun current(): ShakeSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[ENABLED] = enabled }
    suspend fun setThreshold(value: Float) = store.edit { it[THRESHOLD] = value }
    suspend fun setOnlyWhileCharging(value: Boolean) = store.edit { it[ONLY_CHARGING] = value }

    suspend fun setActiveHours(startMinute: Int, endMinute: Int) = store.edit {
        it[ACTIVE_START] = startMinute
        it[ACTIVE_END] = endMinute
    }

    suspend fun heartbeat(nowMillis: Long) = store.edit { it[HEARTBEAT] = nowMillis }

    suspend fun recordRestart(todayEpochDay: Long) = store.edit {
        val (day, count) = RestartCounter.increment(it[RESTART_DAY], it[RESTART_COUNT] ?: 0, todayEpochDay)
        it[RESTART_DAY] = day
        it[RESTART_COUNT] = count
    }

    suspend fun recordLaunchPath(path: String) = store.edit { it[LAST_PATH] = path }

    private companion object {
        val ENABLED = booleanPreferencesKey("shake_enabled")
        val THRESHOLD = floatPreferencesKey("shake_threshold")
        val ACTIVE_START = intPreferencesKey("shake_active_start")
        val ACTIVE_END = intPreferencesKey("shake_active_end")
        val ONLY_CHARGING = booleanPreferencesKey("shake_only_charging")
        val HEARTBEAT = longPreferencesKey("shake_heartbeat")
        val RESTART_DAY = longPreferencesKey("shake_restart_day")
        val RESTART_COUNT = intPreferencesKey("shake_restart_count")
        val LAST_PATH = stringPreferencesKey("shake_last_path")
    }
}
