package com.kairo.app.domain.plan

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import java.time.LocalDate

/**
 * Why something can't happen or deserves attention. Structured (not strings) so the UI can
 * localize it and tests can assert on it.
 */
sealed interface Reason {
    data class NotFound(val query: String) : Reason
    data class Ambiguous(val query: String, val candidates: List<String>) : Reason
    data class FixedBlockImmovable(val blockTitle: String) : Reason
    data class NoFreeSlot(val taskTitle: String, val date: LocalDate) : Reason
    data class NoSlotBeforeDeadline(val taskTitle: String, val deadline: LocalDate) : Reason
    data class DeadlinePassed(val taskTitle: String, val deadline: LocalDate) : Reason
    data class DateInPast(val date: LocalDate) : Reason
    data class TimeInPast(val minute: Int) : Reason
    data class OverBudget(val roleName: String, val plannedMinutes: Int, val budgetMinutes: Int) : Reason

    /** [obstacleTitle] is null when the obstacle was an alarm rather than a block. */
    data class ShiftedAroundFixed(val taskTitle: String, val requestedMinute: Int, val placedMinute: Int, val obstacleTitle: String?) : Reason
    data class OverlapsTask(val taskTitle: String, val otherTitle: String) : Reason
    data class OutsideDayHours(val taskTitle: String) : Reason
    data class PlacedOnLaterDay(val taskTitle: String, val date: LocalDate) : Reason
    data class AlreadyDone(val taskTitle: String) : Reason
    data class AlreadySkipped(val blockTitle: String) : Reason
    data class NothingToRollOver(val date: LocalDate) : Reason
    data object NoRoles : Reason
}

sealed interface Change {
    /** Changes that write something when applied. Each carries exact before/after so undo is mechanical. */
    sealed interface Mutation : Change

    /** A brand-new task (id 0 until applied) placed on the plan. */
    data class Added(val task: Task) : Mutation

    /** An existing task gets a new slot. [before] may be unscheduled (backlog -> day). */
    data class Moved(val before: Task, val after: Task) : Mutation

    data class Removed(val item: RemovedItem) : Mutation

    data class Conflict(val reason: Reason) : Change
    data class Warning(val reason: Reason) : Change
}

/** Things that leave the day's open plan. Nothing here deletes a row: rule 3, never silently delete. */
sealed interface RemovedItem {
    data class TaskUnscheduled(val before: Task, val after: Task) : RemovedItem
    data class TaskCompleted(val before: Task, val after: Task) : RemovedItem
    data class BlockSkipped(val block: FixedBlock, val date: LocalDate) : RemovedItem
}

/** Read-only answer for QueryDay. */
data class Agenda(val date: LocalDate, val entries: List<TimelineEntry>, val nextOnly: Boolean)

data class PlanDiff(val changes: List<Change> = emptyList(), val agenda: Agenda? = null) {
    val mutations: List<Change.Mutation> get() = changes.filterIsInstance<Change.Mutation>()
    val conflicts: List<Reason> get() = changes.filterIsInstance<Change.Conflict>().map { it.reason }
    val warnings: List<Reason> get() = changes.filterIsInstance<Change.Warning>().map { it.reason }
    val canApply: Boolean get() = mutations.isNotEmpty()

    operator fun plus(other: PlanDiff) = PlanDiff(changes + other.changes, agenda ?: other.agenda)

    companion object {
        fun conflict(reason: Reason) = PlanDiff(listOf(Change.Conflict(reason)))
        fun warning(reason: Reason) = PlanDiff(listOf(Change.Warning(reason)))
    }
}
