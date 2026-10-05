package com.kairo.app.ui

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.DayProgress
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.ui.tasks.TasksUiState
import com.kairo.app.ui.timetable.TimetableUiState
import com.kairo.app.ui.today.TodayUiState
import java.time.LocalDate

/** Fixed sample data for @Preview only; never used at runtime. */
object PreviewData {
    private val today = LocalDate.of(2026, 10, 5)

    val roles = listOf(
        Role(1, "College", "#00E5FF", 360),
        Role(2, "Intern", "#B388FF", 240),
        Role(3, "Client", "#39FF88", 120),
        Role(4, "Content", "#FF2E93", 60),
    )

    val blocks = listOf(
        FixedBlock(1, "DBMS lecture", 1, 1, 9 * 60, 10 * 60, "LH-204"),
        FixedBlock(2, "OS lab", 1, 1, 11 * 60, 13 * 60, "Lab 3"),
        FixedBlock(3, "Maths", 1, 3, 10 * 60, 11 * 60),
        FixedBlock(4, "Standup", 2, 2, 18 * 60, 18 * 60 + 30),
    )

    private val todayTasks = listOf(
        Task(1, "Client logo revisions", 3, 60, priority = 2, status = TaskStatus.DONE,
            scheduledEpochDay = today.toEpochDay(), scheduledStartMinute = 14 * 60, createdAt = 0),
        Task(2, "Edit reel #12", 4, 45, priority = 3, status = TaskStatus.SCHEDULED,
            scheduledEpochDay = today.toEpochDay(), scheduledStartMinute = 16 * 60, createdAt = 0),
        Task(3, "Intern report", 2, 90, priority = 1, status = TaskStatus.SCHEDULED,
            scheduledEpochDay = today.toEpochDay(), scheduledStartMinute = 20 * 60, createdAt = 0),
    )

    val todayState = TodayUiState(
        firstName = "Aarav",
        dayPart = DayPart.MORNING,
        date = today,
        entries = TimelineBuilder.build(blocks.filter { it.dayOfWeek == 1 }, todayTasks, roles),
        progress = DayProgress.of(todayTasks),
    )

    val timetableState = TimetableUiState(blocks = blocks, roles = roles)

    val tasksState = TasksUiState(
        backlog = listOf(
            Task(10, "DBMS assignment 3", 1, 120, deadlineEpochDay = today.plusDays(2).toEpochDay(), priority = 1, createdAt = 0),
            Task(11, "Script for YT short", 4, 30, priority = 3, createdAt = 0),
        ),
        carriedOver = listOf(
            Task(12, "Send invoice to Mehta", 3, 15, priority = 2, status = TaskStatus.SCHEDULED,
                scheduledEpochDay = today.minusDays(1).toEpochDay(), scheduledStartMinute = 17 * 60, createdAt = 0),
        ),
        roles = roles,
    )
}
