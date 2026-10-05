package com.kairo.app.data.repository

import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.FixedBlockDao
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.RoleDao
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskDao
import com.kairo.app.data.local.TaskStatus
import kotlinx.coroutines.flow.Flow

class RoleRepository(private val dao: RoleDao) {
    fun allRoles(): Flow<List<Role>> = dao.allRoles()

    /** Returns true only on the launch that actually seeded, so callers can log or react once. */
    suspend fun seedDefaultsIfEmpty(): Boolean = dao.insertIfEmpty(DefaultRoles.all)
}

class TimetableRepository(private val dao: FixedBlockDao) {
    fun blocksForDay(dayOfWeek: Int): Flow<List<FixedBlock>> = dao.blocksForDay(dayOfWeek)
    fun allBlocks(): Flow<List<FixedBlock>> = dao.allBlocks()
    suspend fun save(block: FixedBlock): Long = dao.upsert(block)
    suspend fun delete(block: FixedBlock) = dao.delete(block)
}

class TaskRepository(
    private val dao: TaskDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun tasksForDate(epochDay: Long): Flow<List<Task>> = dao.tasksForDate(epochDay)
    fun unfinishedBefore(epochDay: Long): Flow<List<Task>> = dao.unfinishedBefore(epochDay)
    fun unscheduledOpenTasks(): Flow<List<Task>> = dao.unscheduledOpenTasks()

    suspend fun addTask(
        title: String,
        roleId: Long,
        durationMinutes: Int,
        priority: Int,
        deadlineEpochDay: Long?,
    ): Long = dao.insert(
        Task(
            title = title.trim(),
            roleId = roleId,
            durationMinutes = durationMinutes,
            priority = priority.coerceIn(1, 4),
            deadlineEpochDay = deadlineEpochDay,
            createdAt = now(),
        ),
    )

    /**
     * Manual placement by the user. This is not the scheduler: it only records what the user picked,
     * so the Today timeline has something to show until domain/Scheduler exists.
     */
    suspend fun placeManually(task: Task, epochDay: Long, startMinute: Int) =
        dao.update(task.copy(status = TaskStatus.SCHEDULED, scheduledEpochDay = epochDay, scheduledStartMinute = startMinute))

    /** Tapping a task toggles it, so an accidental tap is undone with a second tap. */
    suspend fun toggleDone(task: Task) {
        val next = when {
            task.status == TaskStatus.DONE && task.scheduledEpochDay != null -> TaskStatus.SCHEDULED
            task.status == TaskStatus.DONE -> TaskStatus.TODO
            else -> TaskStatus.DONE
        }
        dao.setStatus(task.id, next)
    }
}
