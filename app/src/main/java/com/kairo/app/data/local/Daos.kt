package com.kairo.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleDao {
    @Query("SELECT * FROM roles ORDER BY id")
    fun allRoles(): Flow<List<Role>>

    @Query("SELECT COUNT(*) FROM roles")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(roles: List<Role>)

    /** Count + insert in one transaction so two racing launches can't double-seed. */
    @Transaction
    suspend fun insertIfEmpty(roles: List<Role>): Boolean {
        if (count() > 0) return false
        insertAll(roles)
        return true
    }
}

@Dao
interface FixedBlockDao {
    @Query("SELECT * FROM fixed_blocks WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute")
    fun blocksForDay(dayOfWeek: Int): Flow<List<FixedBlock>>

    @Query("SELECT * FROM fixed_blocks ORDER BY dayOfWeek, startMinute")
    fun allBlocks(): Flow<List<FixedBlock>>

    @Upsert
    suspend fun upsert(block: FixedBlock): Long

    @Delete
    suspend fun delete(block: FixedBlock)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledEpochDay = :epochDay ORDER BY scheduledStartMinute")
    fun tasksForDate(epochDay: Long): Flow<List<Task>>

    /** Tasks planned for an earlier day that never got finished or dropped: candidates for carry-over. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE scheduledEpochDay < :epochDay AND status NOT IN ('DONE', 'DROPPED')
        ORDER BY scheduledEpochDay, scheduledStartMinute
        """,
    )
    fun unfinishedBefore(epochDay: Long): Flow<List<Task>>

    /** Backlog for the Tasks screen: anything not done/dropped and not already placed on a day. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status NOT IN ('DONE', 'DROPPED') AND scheduledEpochDay IS NULL
        ORDER BY priority, deadlineEpochDay IS NULL, deadlineEpochDay, createdAt
        """,
    )
    fun unscheduledOpenTasks(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun setStatus(taskId: Long, status: TaskStatus)
}
