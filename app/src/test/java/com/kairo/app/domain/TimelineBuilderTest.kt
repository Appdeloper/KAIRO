package com.kairo.app.domain

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBuilderTest {
    private val college = Role(1, "College", "#00E5FF", 360)
    private val client = Role(2, "Client", "#39FF88", 120)

    private fun block(title: String, start: Int, end: Int) =
        FixedBlock(id = start.toLong(), title = title, roleId = 1, dayOfWeek = 1, startMinute = start, endMinute = end)

    private fun task(id: Long, title: String, start: Int?, status: TaskStatus = TaskStatus.SCHEDULED, roleId: Long = 2) =
        Task(id = id, title = title, roleId = roleId, durationMinutes = 45, status = status,
            scheduledEpochDay = 1, scheduledStartMinute = start, createdAt = 0)

    @Test
    fun mergesBlocksAndTasksByStartTime() {
        val result = TimelineBuilder.build(
            blocks = listOf(block("OS lab", 660, 780), block("DBMS", 540, 600)),
            tasks = listOf(task(1, "Logo", 600), task(2, "Reel", 480)),
            roles = listOf(college, client),
        )
        assertEquals(listOf("Reel", "DBMS", "Logo", "OS lab"), result.map { it.title })
    }

    @Test
    fun blockWinsTieAndTaskEndUsesDuration() {
        val result = TimelineBuilder.build(listOf(block("DBMS", 540, 600)), listOf(task(1, "Logo", 540)), listOf(college, client))
        assertTrue(result[0] is TimelineEntry.Block)
        assertEquals(540 + 45, result[1].endMinute)
    }

    @Test
    fun droppedHiddenAndUntimedTasksGoLast() {
        val result = TimelineBuilder.build(
            blocks = listOf(block("DBMS", 540, 600)),
            tasks = listOf(task(1, "Anytime", null), task(2, "Dropped", 300, TaskStatus.DROPPED)),
            roles = listOf(college, client),
        )
        assertEquals(listOf("DBMS", "Anytime"), result.map { it.title })
        assertNull(result.last().startMinute)
    }

    @Test
    fun missingRoleDoesNotCrash() {
        val result = TimelineBuilder.build(emptyList(), listOf(task(1, "Orphan", 600, roleId = 99)), listOf(college))
        assertNull(result.single().role)
    }

    @Test
    fun progressCountsTasksIgnoringDropped() {
        val tasks = listOf(
            task(1, "a", 1, TaskStatus.DONE),
            task(2, "b", 2, TaskStatus.SCHEDULED),
            task(3, "c", 3, TaskStatus.DROPPED),
        )
        val progress = DayProgress.of(tasks)
        assertEquals(DayProgress(done = 1, total = 2), progress)
        assertEquals(0.5f, progress.fraction)
        assertEquals(0f, DayProgress.of(emptyList()).fraction)
    }
}
