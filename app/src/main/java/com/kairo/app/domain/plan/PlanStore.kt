package com.kairo.app.domain.plan

import com.kairo.app.data.local.Task

/**
 * The narrow write surface CommandExecutor needs. There is deliberately no way to edit a
 * FixedBlock through it: immovability is enforced by the API shape, not only by checks.
 */
interface PlanStore {
    /** Runs [block] atomically: if it throws, nothing it wrote is kept. */
    suspend fun <T> inTransaction(block: suspend () -> T): T

    suspend fun findTask(id: Long): Task?
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(id: Long)

    suspend fun isBlockSkipped(key: BlockSkipKey): Boolean
    suspend fun addBlockSkip(key: BlockSkipKey)
    suspend fun removeBlockSkip(key: BlockSkipKey)
}
