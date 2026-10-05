package com.kairo.app.domain

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus

sealed interface TimelineEntry {
    val startMinute: Int?
    val endMinute: Int?
    val title: String
    val role: Role?

    data class Block(val block: FixedBlock, override val role: Role?, val skipped: Boolean = false) : TimelineEntry {
        override val startMinute get() = block.startMinute
        override val endMinute get() = block.endMinute
        override val title get() = block.title
    }

    data class TaskEntry(val task: Task, override val role: Role?) : TimelineEntry {
        override val startMinute get() = task.scheduledStartMinute
        override val endMinute get() = task.scheduledStartMinute?.plus(task.durationMinutes)
        override val title get() = task.title
        val isDone get() = task.status == TaskStatus.DONE
    }
}

object TimelineBuilder {
    /**
     * Merges the day's lectures and placed tasks into one list ordered by start time.
     * Dropped tasks are hidden; tasks without a start time sink to the bottom as "anytime".
     * On a tie, fixed blocks come first because they can't move and anchor the day visually.
     * Skipped blocks stay visible (marked) so the user can see what they chose to drop today.
     */
    fun build(
        blocks: List<FixedBlock>,
        tasks: List<Task>,
        roles: List<Role>,
        skippedBlockIds: Set<Long> = emptySet(),
    ): List<TimelineEntry> {
        val rolesById = roles.associateBy { it.id }
        val entries = blocks.map { TimelineEntry.Block(it, rolesById[it.roleId], skipped = it.id in skippedBlockIds) } +
            tasks.filter { it.status != TaskStatus.DROPPED }.map { TimelineEntry.TaskEntry(it, rolesById[it.roleId]) }
        return entries.sortedWith(
            compareBy<TimelineEntry> { it.startMinute ?: Int.MAX_VALUE }
                .thenBy { if (it is TimelineEntry.Block) 0 else 1 }
                .thenBy { it.title },
        )
    }
}

data class DayProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total

    companion object {
        /** Lectures aren't "done" by the user, so progress counts only the day's non-dropped tasks. */
        fun of(tasks: List<Task>): DayProgress {
            val counted = tasks.filter { it.status != TaskStatus.DROPPED }
            return DayProgress(done = counted.count { it.status == TaskStatus.DONE }, total = counted.size)
        }
    }
}
