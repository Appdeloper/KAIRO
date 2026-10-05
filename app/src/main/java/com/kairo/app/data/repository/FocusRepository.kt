package com.kairo.app.data.repository

import com.kairo.app.data.local.FocusDao
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.local.TaskDao
import com.kairo.app.domain.focus.ClockReading
import com.kairo.app.domain.focus.FocusLogs
import com.kairo.app.domain.focus.FocusRecovery
import com.kairo.app.domain.focus.FocusRules
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.domain.focus.RecoveryDecision
import com.kairo.app.domain.focus.StartCheck
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/** Focus sessions and logs. All timing rules come from domain/focus; this only reads and writes. */
class FocusRepository(
    private val dao: FocusDao,
    private val taskDao: TaskDao,
    private val clock: () -> ClockReading,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun runningFlow(): Flow<FocusSession?> = dao.runningFlow()
    fun sessionFlow(id: Long): Flow<FocusSession?> = dao.sessionFlow(id)
    suspend fun running(): FocusSession? = dao.running()
    suspend fun find(id: Long): FocusSession? = dao.findById(id)
    fun now(): ClockReading = clock()

    sealed interface StartResult {
        data class Started(val session: FocusSession) : StartResult
        data class Refused(val reason: StartCheck) : StartResult
    }

    suspend fun start(taskId: Long?, label: String, roleId: Long?, minutes: Int): StartResult {
        val check = FocusRules.canStart(dao.running(), minutes)
        if (check != StartCheck.Ok) return StartResult.Refused(check)
        val session = FocusTiming.newSession(taskId, label, roleId, minutes, clock())
        // Re-checked in the transaction: the read above can race a second tap.
        val id = dao.startIfIdle(session) ?: return StartResult.Refused(StartCheck.AlreadyRunning(dao.running() ?: session))
        return StartResult.Started(session.copy(id = id))
    }

    suspend fun extend(id: Long): FocusSession? {
        val current = dao.findById(id)?.takeIf { it.outcome == FocusOutcome.RUNNING } ?: return null
        val extended = FocusTiming.extend(current, clock())
        return if (dao.updateIfRunning(extended)) extended else null
    }

    /**
     * Ends the session and writes its FocusLog. Returns the finished row, or null if it had already
     * ended (so callers don't notify twice).
     */
    suspend fun finish(id: Long, requested: FocusOutcome, endedOnTime: Boolean): FocusSession? {
        val current = dao.findById(id)?.takeIf { it.outcome == FocusOutcome.RUNNING } ?: return null
        val now = clock()
        val ended = current.copy(
            outcome = FocusTiming.finishedOutcome(current, requested),
            actualEndEpochMillis = FocusTiming.actualEndWallMillis(current, now, endedOnTime),
        )
        return if (dao.finishIfRunning(id, ended, FocusLogs.from(ended, zone()))) ended else null
    }

    /** After a reboot / update / force-stop: resume a running session or close it if its end passed. */
    sealed interface Recovered {
        data class Resumed(val session: FocusSession) : Recovered
        data class Closed(val session: FocusSession) : Recovered
    }

    suspend fun recover(): Recovered? {
        val running = dao.running() ?: return null
        return when (val decision = FocusRecovery.decide(running, clock())) {
            is RecoveryDecision.Resume -> {
                dao.updateIfRunning(decision.reanchored)
                Recovered.Resumed(decision.reanchored)
            }
            RecoveryDecision.CloseAsFinished ->
                finish(running.id, FocusOutcome.DONE, endedOnTime = true)?.let { Recovered.Closed(it) }
        }
    }

    suspend fun currentNextStep(session: FocusSession): String? =
        session.taskId?.let { taskDao.findById(it)?.nextStep } ?: session.nextStep

    /** Blank clears it. A task session writes Task.nextStep; a lecture session keeps it on the session row. */
    suspend fun saveNextStep(session: FocusSession, text: String) {
        val value = text.trim().ifEmpty { null }
        val taskId = session.taskId
        if (taskId != null) taskDao.setNextStep(taskId, value) else dao.setSessionNextStep(session.id, value)
    }
}
