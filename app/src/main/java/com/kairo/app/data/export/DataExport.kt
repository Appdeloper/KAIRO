package com.kairo.app.data.export

import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Export my data": everything the user typed in, as readable JSON they can keep or move. Built by
 * hand with org.json so the entities stay plain Room classes.
 */
object DataExport {
    const val FORMAT_VERSION = 1

    fun toJson(roles: List<Role>, blocks: List<FixedBlock>, tasks: List<Task>, alarms: List<Alarm>, exportedAtMillis: Long): String {
        val root = JSONObject()
            .put("app", "KAIRO")
            .put("format", FORMAT_VERSION)
            .put("exportedAt", exportedAtMillis)
            .put("lanes", JSONArray(roles.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorHex).put("dailyBudgetMinutes", it.dailyBudgetMinutes) }))
            .put(
                "timetable",
                JSONArray(
                    blocks.map {
                        JSONObject().put("id", it.id).put("title", it.title).put("laneId", it.roleId).put("dayOfWeek", it.dayOfWeek)
                            .put("startMinute", it.startMinute).put("endMinute", it.endMinute).putOpt("location", it.location)
                    },
                ),
            )
            .put(
                "tasks",
                JSONArray(
                    tasks.map {
                        JSONObject().put("id", it.id).put("title", it.title).put("laneId", it.roleId).put("durationMinutes", it.durationMinutes)
                            .put("priority", it.priority).put("status", it.status.name).putOpt("deadlineEpochDay", it.deadlineEpochDay)
                            .putOpt("scheduledEpochDay", it.scheduledEpochDay).putOpt("scheduledStartMinute", it.scheduledStartMinute)
                            .putOpt("nextStep", it.nextStep)
                    },
                ),
            )
            .put(
                "alarms",
                JSONArray(
                    alarms.map {
                        JSONObject().put("id", it.id).put("label", it.label).put("hour", it.hour).put("minute", it.minute)
                            .put("daysOfWeekMask", it.daysOfWeekMask).put("enabled", it.enabled).put("type", it.type.name)
                    },
                ),
            )
        return root.toString(2)
    }
}
