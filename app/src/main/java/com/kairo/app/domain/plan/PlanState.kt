package com.kairo.app.domain.plan

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import java.time.LocalDate

const val MINUTES_PER_DAY = 24 * 60

/** Rounds up to the next multiple of [step] so suggested times read like "4:10", not "4:07". */
fun roundUpTo(minute: Int, step: Int = 5): Int = ((minute + step - 1) / step) * step

data class BlockSkipKey(val blockId: Long, val epochDay: Long)

/**
 * An immutable snapshot of everything the scheduler may look at. Planning only ever reads this,
 * which is what makes a PlanDiff previewable without touching the database.
 */
data class PlanState(
    val today: LocalDate,
    val nowMinute: Int,
    val nowEpochMillis: Long,
    val wakeMinute: Int,
    val sleepMinute: Int,
    val roles: List<Role>,
    val fixedBlocks: List<FixedBlock>,
    val tasks: List<Task>,
    val skippedBlocks: Set<BlockSkipKey> = emptySet(),
    /** Alarm times (minute of day) per date. Alarms are immovable, like lectures. Filled once alarms exist. */
    val alarmsByDate: Map<LocalDate, List<Int>> = emptyMap(),
    val bufferMinutes: Int = Scheduler.DEFAULT_BUFFER_MINUTES,
) {
    /** Bedtimes after midnight are capped at midnight: v1 plans one calendar day at a time. */
    val dayEndMinute: Int get() = if (sleepMinute > wakeMinute) sleepMinute else MINUTES_PER_DAY

    fun role(id: Long): Role? = roles.firstOrNull { it.id == id }

    fun isSkipped(block: FixedBlock, date: LocalDate) = BlockSkipKey(block.id, date.toEpochDay()) in skippedBlocks

    /** Blocks that actually happen on [date]: right weekday and not skipped for that day. */
    fun blocksOn(date: LocalDate): List<FixedBlock> =
        fixedBlocks.filter { it.dayOfWeek == date.dayOfWeek.value && !isSkipped(it, date) }

    /** Every task placed on [date], any status except dropped. */
    fun tasksOn(date: LocalDate): List<Task> =
        tasks.filter { it.scheduledEpochDay == date.toEpochDay() && it.status != TaskStatus.DROPPED }

    fun alarmsOn(date: LocalDate): List<Int> = alarmsByDate[date].orEmpty()

    /** Earliest minute a new placement may start on [date]: wake time, or "now" for today. */
    fun earliestStartOn(date: LocalDate): Int =
        if (date == today) maxOf(wakeMinute, roundUpTo(nowMinute)) else wakeMinute

    /** Returns a copy with [task] replacing its old version, or appended when it's new (id 0). */
    fun withTask(task: Task): PlanState =
        if (task.id != 0L && tasks.any { it.id == task.id }) {
            copy(tasks = tasks.map { if (it.id == task.id) task else it })
        } else {
            copy(tasks = tasks + task)
        }

    fun withSkip(key: BlockSkipKey): PlanState = copy(skippedBlocks = skippedBlocks + key)

    /** The state as it would be if [diff] were applied; used to chain previews, never persisted. */
    fun afterPreview(diff: PlanDiff): PlanState = diff.mutations.fold(this) { state, change ->
        when (change) {
            is Change.Added -> state.withTask(change.task)
            is Change.Moved -> state.withTask(change.after)
            is Change.Removed -> when (val item = change.item) {
                is RemovedItem.TaskUnscheduled -> state.withTask(item.after)
                is RemovedItem.TaskCompleted -> state.withTask(item.after)
                is RemovedItem.BlockSkipped -> state.withSkip(BlockSkipKey(item.block.id, item.date.toEpochDay()))
            }
        }
    }
}
