package com.kairo.app.domain.plan

import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.TimelineEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** One write that actually happened, recorded so it can be reversed exactly. */
sealed interface AppliedOp {
    data class InsertedTask(val task: Task) : AppliedOp
    data class UpdatedTask(val before: Task, val after: Task) : AppliedOp
    data class AddedSkip(val key: BlockSkipKey) : AppliedOp
}

data class AppliedDiff(val diff: PlanDiff, val ops: List<AppliedOp>)

sealed interface ApplyResult {
    data class Applied(val applied: AppliedDiff) : ApplyResult
    data object NothingToApply : ApplyResult

    /** The data changed between preview and Apply; nothing was written. */
    data object Stale : ApplyResult
}

sealed interface UndoResult {
    data object Undone : UndoResult
    data object NotLatest : UndoResult
    data object Stale : UndoResult
}

private class StaleDiffException : RuntimeException()

/**
 * plan() is pure and previewable; apply()/undo() are the only writes, each in one transaction.
 * Before writing, every row is checked against what the preview showed, so an Apply can never
 * act on data the user didn't see.
 */
class CommandExecutor(private val store: PlanStore, private val historyLimit: Int = DEFAULT_HISTORY) {
    private val history = ArrayDeque<AppliedDiff>()
    private val mutex = Mutex()

    val undoDepth: Int get() = history.size
    fun lastApplied(): AppliedDiff? = history.lastOrNull()

    fun plan(command: Command, state: PlanState): PlanDiff = when (command) {
        is Command.AddTask -> planAdd(command, state)
        is Command.MoveBlock -> planMove(command, state)
        is Command.SkipBlock -> {
            val date = command.date ?: state.today
            withTarget(command.target, date, state) { Scheduler.skipBlock(it, date, state) }
        }
        is Command.CompleteTask -> withTarget(command.target, state.today, state) { Scheduler.completeTask(it) }
        is Command.QueryDay -> PlanDiff(agenda = agenda(command.date ?: state.today, command.nextOnly, state))
        is Command.BrainDump -> planBrainDump(command, state)
    }

    /**
     * Several commands from one sentence ("call add kar aur gym skip kar") become one diff, so the
     * user previews, applies and undoes them together. Each command sees the ones before it.
     */
    fun planAll(commands: List<Command>, state: PlanState): PlanDiff {
        var working = state
        var total = PlanDiff()
        for (command in commands) {
            val step = plan(command, working)
            total += step
            working = working.afterPreview(step)
        }
        return total
    }

    suspend fun apply(diff: PlanDiff): ApplyResult = mutex.withLock {
        if (!diff.canApply) return ApplyResult.NothingToApply
        try {
            val ops = store.inTransaction { diff.mutations.map { applyOne(it) } }
            val applied = AppliedDiff(diff, ops)
            history.addLast(applied)
            while (history.size > historyLimit) history.removeFirst()
            ApplyResult.Applied(applied)
        } catch (e: StaleDiffException) {
            ApplyResult.Stale
        }
    }

    /** Undo is strictly last-in-first-out: reverting an older diff under a newer one could corrupt both. */
    suspend fun undo(lastDiff: AppliedDiff): UndoResult = mutex.withLock {
        if (history.lastOrNull() !== lastDiff) return UndoResult.NotLatest
        try {
            store.inTransaction { lastDiff.ops.asReversed().forEach { revert(it) } }
            history.removeLast()
            UndoResult.Undone
        } catch (e: StaleDiffException) {
            UndoResult.Stale
        }
    }

    private fun planAdd(command: Command.AddTask, state: PlanState): PlanDiff {
        val roleId = command.roleId?.takeIf { state.role(it) != null }
            ?: RoleGuesser.guess(command.title, state.roles)?.id
            ?: state.roles.firstOrNull()?.id
            ?: return PlanDiff.conflict(Reason.NoRoles)
        val task = Task(
            title = command.title.trim(),
            roleId = roleId,
            durationMinutes = (command.durationMinutes ?: Scheduler.DEFAULT_TASK_MINUTES).coerceAtLeast(MIN_DURATION),
            deadlineEpochDay = command.deadline?.toEpochDay(),
            priority = command.priority.coerceIn(1, 4),
            createdAt = state.nowEpochMillis,
        )
        val date = command.date ?: state.today
        return if (command.startMinute != null) {
            Scheduler.placeTaskAt(task, date, command.startMinute, state)
        } else {
            Scheduler.placeTask(task, date, state)
        }
    }

    private fun planMove(command: Command.MoveBlock, state: PlanState): PlanDiff =
        withTarget(command.target, command.toDate ?: state.today, state) { target ->
            val currentDate = (target as? Resolved.TaskTarget)?.task?.scheduledEpochDay?.let(LocalDate::ofEpochDay)
            val toDate = command.toDate ?: currentDate?.takeIf { it >= state.today } ?: state.today
            Scheduler.moveBlock(target, toDate, command.toStartMinute, state)
        }

    /** Each item sees the ones before it, so a brain dump never double-books a slot. */
    private fun planBrainDump(command: Command.BrainDump, state: PlanState): PlanDiff {
        var working = state
        var total = PlanDiff()
        for (item in command.items) {
            val step = planAdd(item, working)
            total += step
            step.placedTask()?.let { working = working.withTask(it) }
        }
        return total
    }

    private fun agenda(date: LocalDate, nextOnly: Boolean, state: PlanState): Agenda {
        val all = TimelineBuilder.build(state.blocksOn(date), state.tasksOn(date), state.roles)
        if (!nextOnly) return Agenda(date, all, nextOnly = false)
        val upcoming = all.filter { entry ->
            val notDone = (entry as? TimelineEntry.TaskEntry)?.isDone != true
            val notOver = date != state.today || (entry.endMinute ?: Int.MAX_VALUE) > state.nowMinute
            notDone && notOver
        }
        return Agenda(date, upcoming.take(1), nextOnly = true)
    }

    private inline fun withTarget(ref: TargetRef, date: LocalDate, state: PlanState, onFound: (Resolved) -> PlanDiff): PlanDiff =
        when (val r = TargetResolver.resolve(ref, date, state)) {
            is Resolution.Found -> onFound(r.target)
            is Resolution.Failed -> PlanDiff.conflict(r.reason)
        }

    private suspend fun applyOne(change: Change.Mutation): AppliedOp = when (change) {
        is Change.Added -> {
            val id = store.insertTask(change.task.copy(id = 0))
            AppliedOp.InsertedTask(change.task.copy(id = id))
        }
        is Change.Moved -> updateTask(change.before, change.after)
        is Change.Removed -> when (val item = change.item) {
            is RemovedItem.TaskUnscheduled -> updateTask(item.before, item.after)
            is RemovedItem.TaskCompleted -> updateTask(item.before, item.after)
            is RemovedItem.BlockSkipped -> {
                val key = BlockSkipKey(item.block.id, item.date.toEpochDay())
                if (store.isBlockSkipped(key)) throw StaleDiffException()
                store.addBlockSkip(key)
                AppliedOp.AddedSkip(key)
            }
        }
    }

    private suspend fun updateTask(before: Task, after: Task): AppliedOp {
        expectTask(before)
        store.updateTask(after)
        return AppliedOp.UpdatedTask(before, after)
    }

    private suspend fun revert(op: AppliedOp) {
        when (op) {
            is AppliedOp.InsertedTask -> {
                expectTask(op.task)
                store.deleteTask(op.task.id)
            }
            is AppliedOp.UpdatedTask -> {
                expectTask(op.after)
                store.updateTask(op.before)
            }
            is AppliedOp.AddedSkip -> {
                if (!store.isBlockSkipped(op.key)) throw StaleDiffException()
                store.removeBlockSkip(op.key)
            }
        }
    }

    private suspend fun expectTask(expected: Task) {
        if (store.findTask(expected.id) != expected) throw StaleDiffException()
    }

    companion object {
        const val DEFAULT_HISTORY = 5
        private const val MIN_DURATION = 5
    }
}
