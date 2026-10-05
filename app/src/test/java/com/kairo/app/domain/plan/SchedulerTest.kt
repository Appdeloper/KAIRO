package com.kairo.app.domain.plan

import com.kairo.app.data.local.TaskStatus
import com.kairo.app.domain.plan.Fixtures.CONTENT
import com.kairo.app.domain.plan.Fixtures.LECTURE_3PM
import com.kairo.app.domain.plan.Fixtures.MONDAY
import com.kairo.app.domain.plan.Fixtures.TUESDAY
import com.kairo.app.domain.plan.Fixtures.block
import com.kairo.app.domain.plan.Fixtures.h
import com.kairo.app.domain.plan.Fixtures.moved
import com.kairo.app.domain.plan.Fixtures.state
import com.kairo.app.domain.plan.Fixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    // ---- free slots ----

    @Test
    fun freeSlots_carveBlocksAndTasksWithBufferOnBothSides() {
        val slots = Scheduler.findFreeSlots(
            date = MONDAY,
            fixedBlocks = listOf(block(1, "DBMS", h(9), h(10)), LECTURE_3PM),
            scheduledTasks = listOf(task(1, "Call", 30, MONDAY, h(12))),
            dayStartMinute = h(7),
            dayEndMinute = h(23),
        )
        assertEquals(
            listOf(Slot(h(7), h(8, 50)), Slot(h(10, 10), h(11, 50)), Slot(h(12, 40), h(14, 50)), Slot(h(16, 10), h(23))),
            slots,
        )
    }

    @Test
    fun freeSlots_ignoreOtherWeekdaysOtherDatesDoneTasksAndRespectAlarms() {
        val slots = Scheduler.findFreeSlots(
            date = MONDAY,
            fixedBlocks = listOf(block(1, "Tue class", h(9), h(10), day = 2)),
            scheduledTasks = listOf(
                task(1, "Done already", 60, MONDAY, h(9), status = TaskStatus.DONE),
                task(2, "Tomorrow", 60, TUESDAY, h(9)),
            ),
            dayStartMinute = h(7),
            dayEndMinute = h(12),
            bufferMinutes = 0,
            alarmMinutes = listOf(h(10)),
        )
        assertEquals(listOf(Slot(h(7), h(10)), Slot(h(10, 1), h(12))), slots)
    }

    @Test
    fun freeSlots_emptyWhenDayBoundsInverted() {
        assertTrue(Scheduler.findFreeSlots(MONDAY, emptyList(), emptyList(), h(12), h(11)).isEmpty())
    }

    // ---- placeTask ----

    @Test
    fun placeTask_takesEarliestSlotThatFitsAfterNow() {
        val s = state(blocks = listOf(block(1, "DBMS", h(9), h(10))), now = h(8))
        // 8:00–8:50 is free but too short for 60 min, so the next slot after the lecture + buffer wins.
        val diff = Scheduler.placeTask(task(0, "Logo", 60), MONDAY, s)
        val placed = (diff.mutations.single() as Change.Added).task
        assertEquals(h(10, 10), placed.scheduledStartMinute)
        assertEquals(MONDAY.toEpochDay(), placed.scheduledEpochDay)
        assertEquals(TaskStatus.SCHEDULED, placed.status)
    }

    @Test
    fun placeTask_existingTaskBecomesMovedNotAdded() {
        val backlog = task(7, "Invoice", 30)
        val diff = Scheduler.placeTask(backlog, MONDAY, state(tasks = listOf(backlog)))
        val move = diff.moved().single()
        assertEquals(backlog, move.before)
        assertEquals(h(8), move.after.scheduledStartMinute)
    }

    @Test
    fun deadline_neverPlacesAfterDeadline() {
        val packedMonday = state(blocks = listOf(block(1, "Fest duty", h(8), h(23))))
        val dueToday = task(0, "Report", 60, deadline = MONDAY)
        val diff = Scheduler.placeTask(dueToday, MONDAY, packedMonday)
        assertFalse(diff.canApply)
        assertEquals(listOf(Reason.NoSlotBeforeDeadline("Report", MONDAY)), diff.conflicts)
    }

    @Test
    fun deadline_usesLaterDayWhenStillBeforeDeadlineAndSaysSo() {
        val packedMonday = state(blocks = listOf(block(1, "Fest duty", h(8), h(23))))
        val diff = Scheduler.placeTask(task(0, "Report", 60, deadline = TUESDAY), MONDAY, packedMonday)
        val placed = (diff.mutations.single() as Change.Added).task
        assertEquals(TUESDAY.toEpochDay(), placed.scheduledEpochDay)
        assertTrue(Reason.PlacedOnLaterDay("Report", TUESDAY) in diff.warnings)
    }

    @Test
    fun deadline_dateAfterDeadlineIsAConflict() {
        val diff = Scheduler.placeTask(task(0, "Report", deadline = MONDAY), TUESDAY, state())
        assertEquals(listOf(Reason.DeadlinePassed("Report", MONDAY)), diff.conflicts)
        val atTime = Scheduler.placeTaskAt(task(0, "Report", deadline = MONDAY), TUESDAY, h(10), state())
        assertEquals(listOf(Reason.DeadlinePassed("Report", MONDAY)), atTime.conflicts)
    }

    @Test
    fun budget_overBudgetIsAWarningNotASilentSkip() {
        val existing = task(1, "Reel edit", 45, MONDAY, h(9), roleId = CONTENT.id)
        val diff = Scheduler.placeTask(task(0, "Thumbnail", 30, roleId = CONTENT.id), MONDAY, state(tasks = listOf(existing)))
        assertTrue("still placed", diff.canApply)
        assertEquals(listOf(Reason.OverBudget("Content", 75, 60)), diff.warnings)
    }

    @Test
    fun budget_countsFixedBlocksOfTheRole() {
        val lectures = listOf(block(1, "Lab", h(9), h(14)), block(2, "Maths", h(15), h(16)))
        val diff = Scheduler.placeTask(task(0, "Assignment", 30, roleId = Fixtures.COLLEGE.id), MONDAY, state(blocks = lectures))
        assertEquals(listOf(Reason.OverBudget("College", 390, 360)), diff.warnings)
    }

    @Test
    fun budget_withinBudgetHasNoWarning() {
        val diff = Scheduler.placeTask(task(0, "Thumbnail", 30, roleId = CONTENT.id), MONDAY, state())
        assertTrue(diff.warnings.isEmpty())
    }

    // ---- lecture conflicts ----

    @Test
    fun lecture_requestedTimeOverlappingLectureShiftsAfterItWithWarning() {
        val diff = Scheduler.placeTaskAt(task(0, "Gym", 60), MONDAY, h(15), state(blocks = listOf(LECTURE_3PM)))
        val placed = (diff.mutations.single() as Change.Added).task
        assertEquals(h(16, 10), placed.scheduledStartMinute)
        assertTrue(Reason.ShiftedAroundFixed("Gym", h(15), h(16, 10), "DBMS lecture") in diff.warnings)
    }

    @Test
    fun lecture_requestedTimeEndingIntoLectureAlsoShifts() {
        val diff = Scheduler.placeTaskAt(task(0, "Gym", 60), MONDAY, h(14, 30), state(blocks = listOf(LECTURE_3PM)))
        assertEquals(h(16, 10), (diff.mutations.single() as Change.Added).task.scheduledStartMinute)
    }

    @Test
    fun lecture_moveBlockOnLectureIsAConflict() {
        val diff = Scheduler.moveBlock(Resolved.BlockTarget(LECTURE_3PM), MONDAY, h(18), state(blocks = listOf(LECTURE_3PM)))
        assertEquals(listOf(Reason.FixedBlockImmovable("DBMS lecture")), diff.conflicts)
        assertFalse(diff.canApply)
    }

    @Test
    fun lecture_skippedLectureFreesItsTimeForThatDayOnly() {
        val skipped = setOf(BlockSkipKey(LECTURE_3PM.id, MONDAY.toEpochDay()))
        val diff = Scheduler.placeTaskAt(task(0, "Gym", 60), MONDAY, h(15), state(blocks = listOf(LECTURE_3PM), skipped = skipped))
        assertEquals(h(15), (diff.mutations.single() as Change.Added).task.scheduledStartMinute)
        assertTrue(diff.warnings.isEmpty())
    }

    @Test
    fun alarm_requestedTimeOverAnAlarmShifts() {
        val diff = Scheduler.placeTaskAt(task(0, "Run", 30), MONDAY, h(9), state(alarms = mapOf(MONDAY to listOf(h(9, 15)))))
        val placed = (diff.mutations.single() as Change.Added).task
        assertEquals(h(9, 30), placed.scheduledStartMinute)
        assertTrue(diff.warnings.single() is Reason.ShiftedAroundFixed)
    }

    @Test
    fun explicitTime_overlappingTaskAndOutsideHoursAreWarnings() {
        val other = task(1, "Call", 60, MONDAY, h(18))
        val diff = Scheduler.placeTaskAt(task(0, "Gym", 60), MONDAY, h(18, 30), state(tasks = listOf(other)))
        assertTrue(diff.canApply)
        assertEquals(listOf(Reason.OverlapsTask("Gym", "Call")), diff.warnings)
        val late = Scheduler.placeTaskAt(task(0, "Movie", 60), MONDAY, h(22, 30), state())
        assertEquals(listOf(Reason.OutsideDayHours("Movie")), late.warnings)
    }

    @Test
    fun explicitTime_inThePastIsAConflict() {
        assertEquals(listOf(Reason.TimeInPast(h(7))), Scheduler.placeTaskAt(task(0, "Gym"), MONDAY, h(7), state(now = h(8))).conflicts)
        assertEquals(listOf(Reason.DateInPast(MONDAY.minusDays(1))), Scheduler.placeTask(task(0, "Gym"), MONDAY.minusDays(1), state()).conflicts)
    }

    // ---- skip / complete ----

    @Test
    fun skip_taskGoesBackToBacklogBlockGetsOneDayException() {
        val gym = task(5, "Gym", 60, MONDAY, h(18))
        val taskDiff = Scheduler.skipBlock(Resolved.TaskTarget(gym), MONDAY, state(tasks = listOf(gym)))
        val unscheduled = (taskDiff.mutations.single() as Change.Removed).item as RemovedItem.TaskUnscheduled
        assertEquals(gym.copy(status = TaskStatus.TODO, scheduledEpochDay = null, scheduledStartMinute = null), unscheduled.after)

        val blockDiff = Scheduler.skipBlock(Resolved.BlockTarget(LECTURE_3PM), MONDAY, state(blocks = listOf(LECTURE_3PM)))
        assertEquals(Change.Removed(RemovedItem.BlockSkipped(LECTURE_3PM, MONDAY)), blockDiff.mutations.single())

        val again = Scheduler.skipBlock(
            Resolved.BlockTarget(LECTURE_3PM), MONDAY,
            state(blocks = listOf(LECTURE_3PM), skipped = setOf(BlockSkipKey(LECTURE_3PM.id, MONDAY.toEpochDay()))),
        )
        assertEquals(listOf(Reason.AlreadySkipped("DBMS lecture")), again.warnings)
        assertFalse(again.canApply)
    }

    @Test
    fun complete_marksDoneAndRefusesBlocks() {
        val t = task(5, "Invoice", 15, MONDAY, h(10))
        val item = (Scheduler.completeTask(Resolved.TaskTarget(t)).mutations.single() as Change.Removed).item
        assertEquals(RemovedItem.TaskCompleted(t, t.copy(status = TaskStatus.DONE)), item)
        assertEquals(listOf(Reason.AlreadyDone("Invoice")), Scheduler.completeTask(Resolved.TaskTarget(t.copy(status = TaskStatus.DONE))).warnings)
        assertEquals(listOf(Reason.FixedBlockImmovable("DBMS lecture")), Scheduler.completeTask(Resolved.BlockTarget(LECTURE_3PM)).conflicts)
    }

    // ---- rollover ----

    @Test
    fun rollover_ordersByPriorityThenDeadline() {
        val leftovers = listOf(
            task(1, "P3", 60, MONDAY, h(9), priority = 3),
            task(2, "P1 due later", 60, MONDAY, h(10), priority = 1, deadline = MONDAY.plusDays(5)),
            task(3, "P1 due soon", 60, MONDAY, h(11), priority = 1, deadline = MONDAY.plusDays(2)),
            task(4, "P2", 60, MONDAY, h(12), priority = 2),
            task(5, "Done", 60, MONDAY, h(13), status = TaskStatus.DONE),
            task(6, "Dropped", 60, MONDAY, h(14), status = TaskStatus.DROPPED),
        )
        val diff = Scheduler.rolloverUnfinished(MONDAY, TUESDAY, state(tasks = leftovers))
        val moves = diff.moved()
        assertEquals(listOf("P1 due soon", "P1 due later", "P2", "P3"), moves.map { it.after.title })
        assertEquals(listOf(h(7), h(8, 10), h(9, 20), h(10, 30)), moves.map { it.after.scheduledStartMinute })
        assertTrue(moves.all { it.after.scheduledEpochDay == TUESDAY.toEpochDay() })
    }

    @Test
    fun rollover_whenDayIsFullTheLowestPriorityIsTheConflict() {
        val tuesdayPacked = block(1, "Hackathon", h(9), h(23), day = 2)
        val leftovers = listOf(task(1, "Low", 90, MONDAY, h(9), priority = 4), task(2, "High", 90, MONDAY, h(10), priority = 1))
        val diff = Scheduler.rolloverUnfinished(MONDAY, TUESDAY, state(blocks = listOf(tuesdayPacked), tasks = leftovers))
        assertEquals(listOf("High"), diff.moved().map { it.after.title })
        assertEquals(listOf(Reason.NoFreeSlot("Low", TUESDAY)), diff.conflicts)
    }

    @Test
    fun rollover_nothingLeftIsAWarning() {
        assertEquals(listOf(Reason.NothingToRollOver(MONDAY)), Scheduler.rolloverUnfinished(MONDAY, TUESDAY, state()).warnings)
    }
}
