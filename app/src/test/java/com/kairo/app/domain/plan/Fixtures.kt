package com.kairo.app.domain.plan

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import java.time.LocalDate

/** Shared test world: Monday 5 Oct 2026, 8:00 now, awake 7:00–23:00. */
object Fixtures {
    val MONDAY: LocalDate = LocalDate.of(2026, 10, 5)
    val TUESDAY: LocalDate = MONDAY.plusDays(1)

    val COLLEGE = Role(1, "College", "#00E5FF", 360)
    val CLIENT = Role(2, "Client", "#39FF88", 120)
    val CONTENT = Role(3, "Content", "#FF2E93", 60)
    val ROLES = listOf(COLLEGE, CLIENT, CONTENT)

    fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    fun block(id: Long, title: String, start: Int, end: Int, day: Int = 1, roleId: Long = COLLEGE.id) =
        FixedBlock(id = id, title = title, roleId = roleId, dayOfWeek = day, startMinute = start, endMinute = end)

    /** The lecture the acceptance criteria protect: Monday 15:00–16:00. */
    val LECTURE_3PM = block(100, "DBMS lecture", h(15), h(16))

    fun task(
        id: Long,
        title: String,
        duration: Int = 60,
        date: LocalDate? = null,
        start: Int? = null,
        priority: Int = 3,
        deadline: LocalDate? = null,
        roleId: Long = CLIENT.id,
        status: TaskStatus = if (date != null) TaskStatus.SCHEDULED else TaskStatus.TODO,
    ) = Task(
        id = id, title = title, roleId = roleId, durationMinutes = duration, deadlineEpochDay = deadline?.toEpochDay(),
        priority = priority, status = status, scheduledEpochDay = date?.toEpochDay(), scheduledStartMinute = start, createdAt = 0,
    )

    fun state(
        blocks: List<FixedBlock> = emptyList(),
        tasks: List<Task> = emptyList(),
        now: Int = h(8),
        alarms: Map<LocalDate, List<Int>> = emptyMap(),
        skipped: Set<BlockSkipKey> = emptySet(),
    ) = PlanState(
        today = MONDAY, nowMinute = now, nowEpochMillis = 1_000L, wakeMinute = h(7), sleepMinute = h(23),
        roles = ROLES, fixedBlocks = blocks, tasks = tasks, skippedBlocks = skipped, alarmsByDate = alarms,
    )

    fun PlanDiff.added(): Task = mutations.filterIsInstance<Change.Added>().single().task
    fun PlanDiff.moved(): List<Change.Moved> = mutations.filterIsInstance<Change.Moved>()
}
