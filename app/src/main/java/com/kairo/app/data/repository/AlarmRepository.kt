package com.kairo.app.data.repository

import com.kairo.app.alarm.AlarmPlan
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.alarm.AlarmPlans.withRuntimeFrom
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmDao
import com.kairo.app.data.local.BlockSkip
import com.kairo.app.data.local.BlockSkipDao
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.FixedBlockDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AlarmRepository(
    private val alarms: AlarmDao,
    private val blocks: FixedBlockDao,
    private val skips: BlockSkipDao,
) {
    fun alarms(): Flow<List<Alarm>> = alarms.allAlarms()

    /** Alarms with block links resolved; re-emits when an alarm, a lecture or a lecture skip changes. */
    fun plans(): Flow<List<AlarmPlan>> = combine(alarms.allAlarms(), blocks.allBlocks(), skips.all()) { a, b, s -> resolve(a, b, s) }

    suspend fun plansOnce(): List<AlarmPlan> = resolve(alarms.allOnce(), blocks.allBlocksOnce(), skips.allOnce())

    suspend fun find(id: Long): Alarm? = alarms.findById(id)

    suspend fun save(alarm: Alarm): Long = if (alarm.id == 0L) alarms.insert(alarm) else alarm.id.also { alarms.update(alarm) }

    suspend fun delete(alarm: Alarm) = alarms.delete(alarm)

    /** Writes ring-time state (snooze count, one-shot disabling, cleared skip) back to Room. */
    suspend fun applyRuntime(plan: AlarmPlan) {
        val stored = alarms.findById(plan.id) ?: return
        val updated = stored.withRuntimeFrom(plan)
        if (updated != stored) alarms.update(updated)
    }

    private fun resolve(all: List<Alarm>, allBlocks: List<FixedBlock>, allSkips: List<BlockSkip>): List<AlarmPlan> {
        val blocksById = allBlocks.associateBy { it.id }
        val skipsByBlock = allSkips.groupBy({ it.blockId }, { it.epochDay })
        return all.map { alarm ->
            val blockId = alarm.linkedBlockId
            AlarmPlans.resolve(alarm, blockId?.let(blocksById::get), blockId?.let { skipsByBlock[it]?.toSet() }.orEmpty())
        }
    }
}
