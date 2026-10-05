package com.kairo.app.domain.plan

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import java.time.LocalDate

/** A free window inside the day, in minutes from midnight. End is exclusive. */
data class Slot(val startMinute: Int, val endMinute: Int) {
    val length: Int get() = endMinute - startMinute

    /** This slot with [start, end) cut out; may split into two. */
    fun minus(start: Int, end: Int): List<Slot> {
        if (end <= startMinute || start >= endMinute) return listOf(this)
        return listOfNotNull(
            Slot(startMinute, start).takeIf { start > startMinute },
            Slot(end, endMinute).takeIf { end < endMinute },
        )
    }
}

/** Something a placement must respect, with what the user would call it. */
private data class Busy(val start: Int, val end: Int, val title: String?) {
    fun overlaps(otherStart: Int, otherEnd: Int) = otherStart < end && otherEnd > start
}

/** What a command points at once the name has been resolved. */
sealed interface Resolved {
    data class TaskTarget(val task: Task) : Resolved
    data class BlockTarget(val block: FixedBlock) : Resolved
}

/**
 * The only place scheduling decisions are made (CLAUDE.md rule 2). Pure functions over a
 * [PlanState] snapshot: no I/O, no clocks, no Android, so every rule is unit-testable.
 *
 * Invariant: nothing here produces a change to a FixedBlock or an alarm (rule 4). Blocks can be
 * skipped for one date, but never moved or edited.
 */
object Scheduler {
    const val DEFAULT_BUFFER_MINUTES = 10
    const val DEFAULT_TASK_MINUTES = 30

    /** Without a deadline, look a week ahead before giving up: further out is a planning conversation, not a slot. */
    private const val SEARCH_DAYS_WITHOUT_DEADLINE = 7

    /** Alarms are instants; give them a minute of width so the buffer math treats them like any busy interval. */
    private const val ALARM_WIDTH_MINUTES = 1

    private val ACTIVE = setOf(TaskStatus.TODO, TaskStatus.SCHEDULED)

    fun findFreeSlots(
        date: LocalDate,
        fixedBlocks: List<FixedBlock>,
        scheduledTasks: List<Task>,
        dayStartMinute: Int,
        dayEndMinute: Int,
        bufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
        alarmMinutes: List<Int> = emptyList(),
    ): List<Slot> {
        if (dayEndMinute <= dayStartMinute) return emptyList()
        val busy = fixedBusy(date, fixedBlocks, alarmMinutes) + taskBusy(date, scheduledTasks)
        // The buffer is carved out on both sides of every busy interval, so a slot already
        // includes the breathing room before and after its neighbours.
        return busy.fold(listOf(Slot(dayStartMinute, dayEndMinute))) { free, b ->
            free.flatMap { it.minus(b.start - bufferMinutes, b.end + bufferMinutes) }
        }.filter { it.length > 0 }
    }

    /**
     * Earliest slot that fits, starting on [date]. With [searchForward] it keeps looking on later
     * days up to the deadline (or a week), so "add X" lands somewhere sensible even on a packed day.
     */
    fun placeTask(task: Task, date: LocalDate, context: PlanState, searchForward: Boolean = true): PlanDiff {
        if (date < context.today) return PlanDiff.conflict(Reason.DateInPast(date))
        val deadline = task.deadlineDate()
        if (deadline != null && date > deadline) return PlanDiff.conflict(Reason.DeadlinePassed(task.title, deadline))

        val lastDate = when {
            !searchForward -> date
            deadline != null -> deadline
            else -> date.plusDays(SEARCH_DAYS_WITHOUT_DEADLINE - 1L)
        }
        var day = date
        while (day <= lastDate) {
            earliestFit(task, day, context, notBefore = context.earliestStartOn(day))?.let { start ->
                val placed = task.scheduledAt(day, start)
                val laterDay = if (day != date) listOf(Change.Warning(Reason.PlacedOnLaterDay(task.title, day))) else emptyList()
                return PlanDiff(listOf(placementChange(task, placed)) + laterDay + budgetWarnings(placed, day, context))
            }
            day = day.plusDays(1)
        }
        return PlanDiff.conflict(
            if (deadline != null && searchForward) Reason.NoSlotBeforeDeadline(task.title, deadline) else Reason.NoFreeSlot(task.title, date),
        )
    }

    /**
     * The user named a time. We honour it unless it collides with something immovable; then we take
     * the next free slot that day and say so, instead of overlapping a lecture or an alarm.
     */
    fun placeTaskAt(task: Task, date: LocalDate, startMinute: Int, context: PlanState): PlanDiff {
        if (date < context.today) return PlanDiff.conflict(Reason.DateInPast(date))
        if (date == context.today && startMinute < context.nowMinute) return PlanDiff.conflict(Reason.TimeInPast(startMinute))
        val deadline = task.deadlineDate()
        if (deadline != null && date > deadline) return PlanDiff.conflict(Reason.DeadlinePassed(task.title, deadline))
        val end = startMinute + task.durationMinutes
        if (end > MINUTES_PER_DAY) return PlanDiff.conflict(Reason.NoFreeSlot(task.title, date))

        val obstacle = fixedBusy(date, context.blocksOn(date), context.alarmsOn(date)).firstOrNull { it.overlaps(startMinute, end) }
        if (obstacle != null) {
            val shifted = earliestFit(task, date, context, notBefore = maxOf(startMinute, context.earliestStartOn(date)))
                ?: return PlanDiff.conflict(Reason.NoFreeSlot(task.title, date))
            val placed = task.scheduledAt(date, shifted)
            val note = Change.Warning(Reason.ShiftedAroundFixed(task.title, startMinute, shifted, obstacle.title))
            return PlanDiff(listOf(placementChange(task, placed), note) + budgetWarnings(placed, date, context))
        }

        val placed = task.scheduledAt(date, startMinute)
        val warnings = buildList {
            // Overlapping another task is allowed (the user asked for this time) but never silent.
            otherTasksOn(date, task, context)
                .filter { it.status in ACTIVE && it.scheduledStartMinute != null && Busy(it.scheduledStartMinute, it.scheduledStartMinute + it.durationMinutes, null).overlaps(startMinute, end) }
                .forEach { add(Change.Warning(Reason.OverlapsTask(task.title, it.title))) }
            if (startMinute < context.wakeMinute || end > context.dayEndMinute) add(Change.Warning(Reason.OutsideDayHours(task.title)))
        }
        return PlanDiff(listOf(placementChange(task, placed)) + warnings + budgetWarnings(placed, date, context))
    }

    fun moveBlock(target: Resolved, toDate: LocalDate, toStartMinute: Int?, context: PlanState): PlanDiff = when (target) {
        is Resolved.BlockTarget -> PlanDiff.conflict(Reason.FixedBlockImmovable(target.block.title))
        is Resolved.TaskTarget ->
            if (toStartMinute == null) placeTask(target.task, toDate, context) else placeTaskAt(target.task, toDate, toStartMinute, context)
    }

    /** Skipping a lecture is a one-day exception, not a move: the weekly block stays exactly as it is. */
    fun skipBlock(target: Resolved, date: LocalDate, context: PlanState): PlanDiff = when (target) {
        is Resolved.BlockTarget ->
            if (context.isSkipped(target.block, date)) {
                PlanDiff.warning(Reason.AlreadySkipped(target.block.title))
            } else {
                PlanDiff(listOf(Change.Removed(RemovedItem.BlockSkipped(target.block, date))))
            }
        is Resolved.TaskTarget -> {
            // Back to the backlog rather than deleted, so it can be re-planned later.
            val after = target.task.copy(status = TaskStatus.TODO, scheduledEpochDay = null, scheduledStartMinute = null)
            PlanDiff(listOf(Change.Removed(RemovedItem.TaskUnscheduled(target.task, after))))
        }
    }

    fun completeTask(target: Resolved): PlanDiff = when (target) {
        is Resolved.BlockTarget -> PlanDiff.conflict(Reason.FixedBlockImmovable(target.block.title))
        is Resolved.TaskTarget ->
            if (target.task.status == TaskStatus.DONE) {
                PlanDiff.warning(Reason.AlreadyDone(target.task.title))
            } else {
                PlanDiff(listOf(Change.Removed(RemovedItem.TaskCompleted(target.task, target.task.copy(status = TaskStatus.DONE)))))
            }
    }

    /**
     * Re-places what was left open on [fromDate] onto [toDate]. Most important first, so when the
     * day is too small it's the P4s that end up as conflicts, not the P1s.
     */
    fun rolloverUnfinished(fromDate: LocalDate, toDate: LocalDate, context: PlanState): PlanDiff {
        val open = context.tasks
            .filter { it.scheduledEpochDay == fromDate.toEpochDay() && it.status in ACTIVE }
            .sortedWith(ROLLOVER_ORDER)
        if (open.isEmpty()) return PlanDiff.warning(Reason.NothingToRollOver(fromDate))

        var working = context
        var diff = PlanDiff()
        for (task in open) {
            val step = placeTask(task, toDate, working, searchForward = false)
            diff += step
            step.placedTask()?.let { working = working.withTask(it) }
        }
        return diff
    }

    val ROLLOVER_ORDER: Comparator<Task> = compareBy<Task> { it.priority }
        .thenBy(nullsLast()) { it.deadlineEpochDay }
        .thenBy(nullsLast()) { it.scheduledStartMinute }
        .thenBy { it.id }

    private fun earliestFit(task: Task, date: LocalDate, context: PlanState, notBefore: Int): Int? {
        val slots = findFreeSlots(
            date = date,
            fixedBlocks = context.blocksOn(date),
            scheduledTasks = otherTasksOn(date, task, context),
            dayStartMinute = notBefore,
            dayEndMinute = context.dayEndMinute,
            bufferMinutes = context.bufferMinutes,
            alarmMinutes = context.alarmsOn(date),
        )
        return slots.firstNotNullOfOrNull { slot ->
            roundUpTo(slot.startMinute).takeIf { it + task.durationMinutes <= slot.endMinute }
        }
    }

    /** Tasks on [date] other than [task] itself. New tasks all have id 0, so they never exclude each other. */
    private fun otherTasksOn(date: LocalDate, task: Task, context: PlanState) =
        context.tasksOn(date).filter { it.id == 0L || it.id != task.id }

    private fun budgetWarnings(placed: Task, date: LocalDate, context: PlanState): List<Change> {
        val role = context.role(placed.roleId) ?: return emptyList()
        val taskMinutes = otherTasksOn(date, placed, context).filter { it.roleId == role.id }.sumOf { it.durationMinutes }
        val blockMinutes = context.blocksOn(date).filter { it.roleId == role.id }.sumOf { it.endMinute - it.startMinute }
        val planned = taskMinutes + blockMinutes + placed.durationMinutes
        // Over budget is a warning, not a refusal: the user decides whether the day is worth it.
        return if (planned > role.dailyBudgetMinutes) {
            listOf(Change.Warning(Reason.OverBudget(role.name, planned, role.dailyBudgetMinutes)))
        } else {
            emptyList()
        }
    }

    private fun fixedBusy(date: LocalDate, blocks: List<FixedBlock>, alarmMinutes: List<Int>): List<Busy> =
        blocks.filter { it.dayOfWeek == date.dayOfWeek.value }.map { Busy(it.startMinute, it.endMinute, it.title) } +
            alarmMinutes.map { Busy(it, it + ALARM_WIDTH_MINUTES, null) }

    private fun taskBusy(date: LocalDate, tasks: List<Task>): List<Busy> = tasks
        .filter { it.scheduledEpochDay == date.toEpochDay() && it.status in ACTIVE }
        .mapNotNull { t -> t.scheduledStartMinute?.let { Busy(it, it + t.durationMinutes, t.title) } }

    private fun placementChange(original: Task, placed: Task): Change.Mutation =
        if (original.id == 0L) Change.Added(placed) else Change.Moved(original, placed)

    private fun Task.scheduledAt(date: LocalDate, startMinute: Int) =
        copy(status = TaskStatus.SCHEDULED, scheduledEpochDay = date.toEpochDay(), scheduledStartMinute = startMinute)

    private fun Task.deadlineDate(): LocalDate? = deadlineEpochDay?.let(LocalDate::ofEpochDay)
}

/** The task a placement diff puts on the plan, if it put one. */
fun PlanDiff.placedTask(): Task? = mutations.firstNotNullOfOrNull {
    when (it) {
        is Change.Added -> it.task
        is Change.Moved -> it.after
        is Change.Removed -> null
    }
}
