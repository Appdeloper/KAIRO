package com.kairo.app.domain.plan

import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.plan.Fixtures.LECTURE_3PM
import com.kairo.app.domain.plan.Fixtures.MONDAY
import com.kairo.app.domain.plan.Fixtures.TUESDAY
import com.kairo.app.domain.plan.Fixtures.block
import com.kairo.app.domain.plan.Fixtures.h
import com.kairo.app.domain.plan.Fixtures.state
import com.kairo.app.domain.plan.Fixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan() is pure, so these run without any store; the store is never touched. */
class CommandExecutorPlanTest {
    private val executor = CommandExecutor(NoWriteStore)

    @Test
    fun addTask_guessesRoleAndUsesDefaultDuration() {
        val diff = executor.plan(Command.AddTask("Edit reel", startMinute = h(18)), state())
        val added = (diff.mutations.single() as Change.Added).task
        assertEquals(Fixtures.CONTENT.id, added.roleId)
        assertEquals(Scheduler.DEFAULT_TASK_MINUTES, added.durationMinutes)
        assertEquals(h(18), added.scheduledStartMinute)
        assertEquals(1_000L, added.createdAt)
    }

    @Test
    fun brainDump_itemsDoNotDoubleBook() {
        val dump = Command.BrainDump(listOf(Command.AddTask("A", durationMinutes = 60), Command.AddTask("B", durationMinutes = 60)))
        val starts = executor.plan(dump, state()).mutations.map { (it as Change.Added).task.scheduledStartMinute }
        assertEquals(listOf(h(8), h(9, 10)), starts)
    }

    @Test
    fun skipByName_resolvesTodaysBlockAndTask() {
        val gymTask = task(9, "Gym", 60, MONDAY, h(18))
        val s = state(blocks = listOf(LECTURE_3PM), tasks = listOf(gymTask))
        val skipGym = executor.plan(Command.SkipBlock(TargetRef.ByName("gym")), s)
        assertTrue((skipGym.mutations.single() as Change.Removed).item is RemovedItem.TaskUnscheduled)
        val skipLecture = executor.plan(Command.SkipBlock(TargetRef.ByName("dbms")), s)
        assertEquals(RemovedItem.BlockSkipped(LECTURE_3PM, MONDAY), (skipLecture.mutations.single() as Change.Removed).item)
    }

    @Test
    fun resolver_notFoundAndAmbiguousAreConflicts() {
        val s = state(tasks = listOf(task(1, "Client call", date = MONDAY, start = h(10)), task(2, "Client mail", date = MONDAY, start = h(12))))
        assertEquals(listOf(Reason.NotFound("yoga")), executor.plan(Command.SkipBlock(TargetRef.ByName("yoga")), s).conflicts)
        val ambiguous = executor.plan(Command.CompleteTask(TargetRef.ByName("client")), s).conflicts.single() as Reason.Ambiguous
        assertEquals(setOf("Client call", "Client mail"), ambiguous.candidates.toSet())
    }

    @Test
    fun queryDay_listsTheDayAndWhatsNextSkipsThePast() {
        val s = state(blocks = listOf(block(1, "DBMS", h(9), h(10)), LECTURE_3PM), tasks = listOf(task(1, "Call", 30, MONDAY, h(12))), now = h(11))
        val day = executor.plan(Command.QueryDay(), s)
        assertFalse(day.canApply)
        assertEquals(listOf("DBMS", "Call", "DBMS lecture"), day.agenda!!.entries.map { it.title })
        val next = executor.plan(Command.QueryDay(nextOnly = true), s).agenda!!
        assertEquals(listOf("Call"), next.entries.map { it.title })
        val tomorrow = executor.plan(Command.QueryDay(date = TUESDAY), s).agenda!!
        assertTrue(tomorrow.entries.isEmpty())
    }

    /** Acceptance: a 3 pm lecture is never moved by any command. */
    @Test
    fun lectureAt3pm_isNeverMovedByAnyCommand() {
        val gym = task(1, "Gym", 60, MONDAY, h(18))
        val leftover = task(2, "Leftover", 60, MONDAY.minusDays(7), h(15))
        val s = state(blocks = listOf(LECTURE_3PM), tasks = listOf(gym, leftover), now = h(8))
        val lectureRange = h(15) until h(16)
        val commands = listOf(
            Command.AddTask("Call", startMinute = h(15)),
            Command.AddTask("Long task", durationMinutes = 7 * 60),
            Command.MoveBlock(TargetRef.ByName("DBMS lecture"), toStartMinute = h(18)),
            Command.MoveBlock(TargetRef.BlockId(LECTURE_3PM.id), toDate = TUESDAY, toStartMinute = h(9)),
            Command.MoveBlock(TargetRef.ByName("gym"), toStartMinute = h(15, 30)),
            Command.CompleteTask(TargetRef.ByName("dbms")),
            Command.SkipBlock(TargetRef.ByName("gym")),
            Command.QueryDay(),
            Command.BrainDump(List(8) { Command.AddTask("Item $it", durationMinutes = 60) }),
        )
        for (command in commands) {
            val diff = executor.plan(command, s)
            for (m in diff.mutations) {
                val placed: Task? = when (m) {
                    is Change.Added -> m.task
                    is Change.Moved -> m.after
                    is Change.Removed -> null
                }
                if (placed?.scheduledEpochDay == MONDAY.toEpochDay()) {
                    val start = placed.scheduledStartMinute!!
                    val end = start + placed.durationMinutes
                    assertFalse("$command placed ${placed.title} over the lecture", start < lectureRange.last + 1 && end > lectureRange.first)
                }
            }
        }
        val rollover = Scheduler.rolloverUnfinished(MONDAY.minusDays(7), MONDAY, s)
        assertTrue(rollover.moved().all { it.after.scheduledStartMinute != h(15) })
        // And the block itself is untouched: no Change type can carry a modified FixedBlock.
        assertEquals(LECTURE_3PM, s.fixedBlocks.single())
    }

    private fun PlanDiff.moved() = mutations.filterIsInstance<Change.Moved>()

    @Test
    fun undoDepthStartsEmpty() {
        assertEquals(0, executor.undoDepth)
        assertNull(executor.lastApplied())
    }

    @Test
    fun timelineEntryTitlesAreUsedInAgenda() {
        val agenda = executor.plan(Command.QueryDay(), state(blocks = listOf(LECTURE_3PM))).agenda!!
        assertTrue(agenda.entries.single() is TimelineEntry.Block)
    }

    private object NoWriteStore : PlanStore {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = error("plan() must not write")
        override suspend fun findTask(id: Long) = error("plan() must not read the store")
        override suspend fun insertTask(task: Task) = error("no writes")
        override suspend fun updateTask(task: Task) = error("no writes")
        override suspend fun deleteTask(id: Long) = error("no writes")
        override suspend fun isBlockSkipped(key: BlockSkipKey) = error("no reads")
        override suspend fun addBlockSkip(key: BlockSkipKey) = error("no writes")
        override suspend fun removeBlockSkip(key: BlockSkipKey) = error("no writes")
    }
}
