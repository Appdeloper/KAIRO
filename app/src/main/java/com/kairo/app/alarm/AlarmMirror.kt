package com.kairo.app.alarm

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A copy of every alarm in device-protected storage. Room lives in credential-encrypted storage,
 * which is locked after a reboot until the user unlocks; this copy lets alarms be rescheduled on
 * LOCKED_BOOT_COMPLETED and ring before the first unlock.
 */
class AlarmMirror(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): List<AlarmPlan> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(SERIALIZER, raw)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    fun find(id: Long): AlarmPlan? = read().firstOrNull { it.id == id }

    @Synchronized
    fun write(plans: List<AlarmPlan>) {
        // commit (not apply): the process may be killed right after an alarm is handled.
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(SERIALIZER, plans)) }
    }

    @Synchronized
    fun upsert(plan: AlarmPlan) {
        write(read().filterNot { it.id == plan.id } + plan)
    }

    private companion object {
        const val FILE = "alarm_mirror"
        const val KEY = "plans"
        val SERIALIZER = ListSerializer(AlarmPlan.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
