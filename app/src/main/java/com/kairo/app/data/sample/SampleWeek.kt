package com.kairo.app.data.sample

import com.kairo.app.alarm.AlarmDays
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import java.time.LocalDate

/** Everything "Load a sample week" inserts. Built purely so it can be unit-tested. */
data class SampleData(val blocks: List<FixedBlock>, val tasks: List<Task>, val alarm: Alarm)

/**
 * A realistic week for a student with an internship, freelance clients and content work, so beta
 * testers can explore without typing a timetable. Sample text is data, not UI copy, like DefaultRoles.
 * Everything here is removed by Settings > Data > Reset all data.
 */
object SampleWeek {
    private const val H = 60

    /** [roles] in lane order (College, Intern, Client, Content), as seeded. Needs at least four. */
    fun build(today: LocalDate, roles: List<Role>, nowMillis: Long): SampleData {
        require(roles.size >= 4) { "sample week needs the four default lanes" }
        val (college, intern, client, content) = roles.sortedBy { it.id }.take(4).map { it.id }
        fun block(title: String, role: Long, day: Int, start: Int, end: Int, where: String? = null) =
            FixedBlock(title = title, roleId = role, dayOfWeek = day, startMinute = start, endMinute = end, location = where)

        val blocks = listOf(
            block("DBMS lecture", college, 1, 9 * H, 10 * H, "LH-204"),
            block("OS lab", college, 1, 11 * H, 13 * H, "Lab 3"),
            block("Maths", college, 2, 10 * H, 11 * H, "LH-101"),
            block("Computer Networks", college, 2, 14 * H, 15 * H, "LH-204"),
            block("Team standup", intern, 2, 18 * H, 18 * H + 30),
            block("DBMS lecture", college, 3, 9 * H, 10 * H, "LH-204"),
            block("Software Engineering", college, 3, 12 * H, 13 * H, "LH-305"),
            block("Operating Systems", college, 4, 10 * H, 11 * H, "LH-101"),
            block("Networks lab", college, 4, 14 * H, 16 * H, "Lab 2"),
            block("Team standup", intern, 4, 18 * H, 18 * H + 30),
            block("Maths", college, 5, 9 * H, 10 * H, "LH-101"),
            block("Project meeting", college, 5, 11 * H, 12 * H, "Library"),
        )

        val day = today.toEpochDay()
        fun task(title: String, role: Long, minutes: Int, priority: Int = 3, onDay: Long? = null, at: Int? = null, deadline: Long? = null, status: TaskStatus? = null) = Task(
            title = title,
            roleId = role,
            durationMinutes = minutes,
            priority = priority,
            status = status ?: if (onDay != null) TaskStatus.SCHEDULED else TaskStatus.TODO,
            scheduledEpochDay = onDay,
            scheduledStartMinute = at,
            deadlineEpochDay = deadline,
            createdAt = nowMillis,
        )
        val tasks = listOf(
            task("Reply to recruiter", intern, 15, 2, day, 8 * H + 30, status = TaskStatus.DONE),
            task("Client logo revisions", client, 60, 2, day, 17 * H),
            task("Edit reel #12", content, 45, 3, day, 18 * H + 45),
            task("Intern weekly report", intern, 90, 1, day, 20 * H + 30),
            task("DBMS assignment 3", college, 120, 1, day + 1, 16 * H, deadline = day + 2),
            task("Client call prep", client, 30, 2, day + 2, 17 * H),
            task("Script for YT short", content, 30, 3),
            task("Send invoice to Mehta", client, 15, 2),
            task("Read OS chapter 5", college, 60, 3, deadline = day + 3),
        )

        val alarm = Alarm(label = "Wake up", hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS, type = AlarmType.WAKE)
        return SampleData(blocks, tasks, alarm)
    }
}
