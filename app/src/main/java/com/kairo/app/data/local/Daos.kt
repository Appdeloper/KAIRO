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

    @Query("SELECT * FROM fixed_blocks")
    suspend fun allBlocksOnce(): List<FixedBlock>

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

    /** Snapshot for planning. Fine for one person's data; revisit if task history grows large. */
    @Query("SELECT * FROM tasks WHERE status != 'DROPPED'")
    suspend fun allLiveTasksOnce(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): Task?

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun allTasksOnce(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun setStatus(taskId: Long, status: TaskStatus)

    @Query("UPDATE tasks SET nextStep = :nextStep WHERE id = :taskId")
    suspend fun setNextStep(taskId: Long, nextStep: String?)
}

@Dao
interface BlockSkipDao {
    @Query("SELECT blockId FROM block_skips WHERE epochDay = :epochDay")
    fun skippedBlockIdsOn(epochDay: Long): Flow<List<Long>>

    @Query("SELECT * FROM block_skips WHERE epochDay >= :fromEpochDay")
    suspend fun skipsFromOnce(fromEpochDay: Long): List<BlockSkip>

    @Query("SELECT * FROM block_skips ORDER BY blockId, epochDay")
    suspend fun allOnce(): List<BlockSkip>

    @Query("SELECT * FROM block_skips ORDER BY blockId, epochDay")
    fun all(): Flow<List<BlockSkip>>

    @Query("SELECT COUNT(*) > 0 FROM block_skips WHERE blockId = :blockId AND epochDay = :epochDay")
    suspend fun exists(blockId: Long, epochDay: Long): Boolean

    @Insert
    suspend fun insert(skip: BlockSkip)

    @Delete
    suspend fun delete(skip: BlockSkip)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun allAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms ORDER BY id")
    suspend fun allOnce(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun findById(id: Long): Alarm?

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE outcome = 'RUNNING' LIMIT 1")
    fun runningFlow(): Flow<FocusSession?>

    @Query("SELECT * FROM focus_sessions WHERE outcome = 'RUNNING' LIMIT 1")
    suspend fun running(): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun findById(id: Long): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    fun sessionFlow(id: Long): Flow<FocusSession?>

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Update
    suspend fun update(session: FocusSession)

    @Insert
    suspend fun insertLog(log: FocusLog): Long

    @Query("SELECT * FROM focus_logs ORDER BY id")
    suspend fun allLogs(): List<FocusLog>

    @Query("UPDATE focus_sessions SET nextStep = :text WHERE id = :sessionId")
    suspend fun setSessionNextStep(sessionId: Long, text: String?)

    /** The one-running rule lives here, in a transaction, so two quick taps can't start two sessions. */
    @Transaction
    suspend fun startIfIdle(session: FocusSession): Long? {
        if (running() != null) return null
        return insert(session)
    }

    /**
     * Ends a session and logs it exactly once. The RUNNING check makes Done-from-notification racing
     * the end alarm harmless: whichever arrives second finds nothing to end.
     */
    @Transaction
    suspend fun finishIfRunning(id: Long, ended: FocusSession, log: FocusLog): Boolean {
        val current = findById(id) ?: return false
        if (current.outcome != FocusOutcome.RUNNING) return false
        update(ended)
        insertLog(log)
        return true
    }

    /** Extend, or re-anchor after a reboot; only while still running. */
    @Transaction
    suspend fun updateIfRunning(session: FocusSession): Boolean {
        val current = findById(session.id) ?: return false
        if (current.outcome != FocusOutcome.RUNNING) return false
        update(session)
        return true
    }
}
