package com.kairo.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kairo.app.ai.BestGapDto
import com.kairo.app.ai.BriefDto
import com.kairo.app.ai.WireJson
import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefKey
import com.kairo.app.domain.brief.CachedBrief
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** The last cloud brief plus the last plan version we tried to fetch one for. */
class BriefCacheStore(private val store: DataStore<Preferences>) {

    @Serializable
    private data class Stored(val epochDay: Long, val planHash: String, val brief: BriefDto)

    @Serializable
    private data class Attempt(val epochDay: Long, val planHash: String)

    suspend fun cached(): CachedBrief? = read(CACHE, Stored.serializer())?.let {
        CachedBrief(BriefKey(it.epochDay, it.planHash), it.brief.toDomain())
    }

    suspend fun save(key: BriefKey, brief: Brief) {
        write(CACHE, WireJson.encodeToString(Stored.serializer(), Stored(key.epochDay, key.planHash, brief.toDto())))
    }

    suspend fun lastAttempt(): BriefKey? = read(ATTEMPT, Attempt.serializer())?.let { BriefKey(it.epochDay, it.planHash) }

    suspend fun recordAttempt(key: BriefKey) {
        write(ATTEMPT, WireJson.encodeToString(Attempt.serializer(), Attempt(key.epochDay, key.planHash)))
    }

    suspend fun clearAttempt() {
        store.edit { it.remove(ATTEMPT) }
    }

    private suspend fun <T> read(key: Preferences.Key<String>, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val raw = store.data.first()[key] ?: return null
        // A cache written by an older version is just a cache miss, never a crash.
        return try {
            WireJson.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private suspend fun write(key: Preferences.Key<String>, value: String) {
        store.edit { it[key] = value }
    }

    private fun BriefDto.toDomain() = Brief(greeting, summary, bestGap?.let { BestGap(it.startMinute, it.endMinute, it.suggestion) }, ifThenPlans)

    private fun Brief.toDto() = BriefDto(greeting, summary, bestGap?.let { BestGapDto(it.startMinute, it.endMinute, it.suggestion) }, ifThenPlans)

    private companion object {
        val CACHE = stringPreferencesKey("brief_cache")
        val ATTEMPT = stringPreferencesKey("brief_last_attempt")
    }
}
