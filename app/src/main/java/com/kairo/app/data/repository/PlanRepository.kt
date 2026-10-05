package com.kairo.app.data.repository

import androidx.room.withTransaction
import com.kairo.app.data.local.BlockSkip
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.local.Task
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.domain.plan.BlockSkipKey
import com.kairo.app.domain.plan.PlanState
import com.kairo.app.domain.plan.PlanStore
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.flow.first

/**
 * Bridges the pure domain planner to Room. Reads produce a [PlanState] snapshot; writes happen
 * only through [PlanStore], always inside CommandExecutor's single transaction.
 */
class PlanRepository(
    private val db: KairoDatabase,
    private val prefs: UserPrefsRepository,
    private val dateProvider: DateProvider,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : PlanStore {

    suspend fun loadState(): PlanState {
        val today = dateProvider.today()
        val profile = prefs.prefs.first()
        return PlanState(
            today = today,
            nowMinute = dateProvider.nowMinuteOfDay(),
            nowEpochMillis = clockMillis(),
            wakeMinute = profile.wakeMinute,
            sleepMinute = profile.sleepMinute,
            roles = db.roleDao().allRoles().first(),
            fixedBlocks = db.fixedBlockDao().allBlocksOnce(),
            tasks = db.taskDao().allLiveTasksOnce(),
            // Only today onward matters for planning; yesterday is kept so rollover can still see it.
            skippedBlocks = db.blockSkipDao().skipsFromOnce(today.toEpochDay() - 1)
                .map { BlockSkipKey(it.blockId, it.epochDay) }.toSet(),
            // Alarms join in the alarm step; until then nothing extra is immovable.
            alarmsByDate = emptyMap(),
        )
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun findTask(id: Long): Task? = db.taskDao().findById(id)
    override suspend fun insertTask(task: Task): Long = db.taskDao().insert(task)
    override suspend fun updateTask(task: Task) = db.taskDao().update(task)
    override suspend fun deleteTask(id: Long) = db.taskDao().deleteById(id)

    override suspend fun isBlockSkipped(key: BlockSkipKey): Boolean = db.blockSkipDao().exists(key.blockId, key.epochDay)
    override suspend fun addBlockSkip(key: BlockSkipKey) = db.blockSkipDao().insert(BlockSkip(key.blockId, key.epochDay))
    override suspend fun removeBlockSkip(key: BlockSkipKey) = db.blockSkipDao().delete(BlockSkip(key.blockId, key.epochDay))
}
