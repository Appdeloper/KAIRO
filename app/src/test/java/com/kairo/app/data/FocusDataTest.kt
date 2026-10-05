package com.kairo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.local.Task
import com.kairo.app.data.repository.FocusRepository
import com.kairo.app.data.repository.FocusRepository.Recovered
import com.kairo.app.data.repository.FocusRepository.StartResult
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.domain.focus.ClockReading
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.domain.focus.StartCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class FocusDataTest {
    private lateinit var db: KairoDatabase
    private lateinit var repo: FocusRepository
    private var now = ClockReading(wallMillis = 1_790_000_000_000, elapsedMillis = 1_000_000, bootCount = 3)
    private var taskId = 0L
    private var roleId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).allowMainThreadQueries().build()
        RoleRepository(db.roleDao()).seedDefaultsIfEmpty()
        roleId = db.roleDao().allRoles().first().first().id
        taskId = db.taskDao().insert(Task(title = "Edit reel", roleId = roleId, durationMinutes = 45, createdAt = 0))
        repo = FocusRepository(db.focusDao(), db.taskDao(), clock = { now }, zone = { ZoneId.of("Asia/Kolkata") })
    }

    @After
    fun tearDown() = db.close()

    private fun advance(ms: Long) {
        now = now.copy(wallMillis = now.wallMillis + ms, elapsedMillis = now.elapsedMillis + ms)
    }

    private suspend fun startTask(minutes: Int = 25) = (repo.start(taskId, "Edit reel", roleId, minutes) as StartResult.Started).session

    @Test
    fun start_linksTheTask_andIsTheRunningSession() = runTest {
        val s = startTask()
        assertEquals(taskId, s.taskId)
        assertEquals(s.id, repo.running()?.id)
        assertEquals(s.id, db.focusDao().runningFlow().first()?.id)
    }

    @Test
    fun onlyOneRunning_secondStartIsRefused() = runTest {
        val first = startTask()
        val second = repo.start(null, "DBMS lecture", roleId, 50)
        assertTrue(second is StartResult.Refused)
        assertEquals(first.id, (((second as StartResult.Refused).reason) as StartCheck.AlreadyRunning).current.id)
    }

    @Test
    fun daoTransaction_refusesSecondRunningRow() = runTest {
        val s = startTask()
        assertNull(db.focusDao().startIfIdle(s.copy(id = 0)))
    }

    @Test
    fun done_writesOutcomeAndOneLog_secondFinishIsANoOp() = runTest {
        val s = startTask()
        advance(25 * MIN)
        val ended = repo.finish(s.id, FocusOutcome.DONE, endedOnTime = true)
        assertEquals(FocusOutcome.DONE, ended?.outcome)
        assertEquals(s.plannedEndEpochMillis, ended?.actualEndEpochMillis)
        // Done tapped in the notification racing the end alarm: the second one finds nothing to end.
        assertNull(repo.finish(s.id, FocusOutcome.DONE, endedOnTime = true))
        val logs = db.focusDao().allLogs()
        assertEquals(1, logs.size)
        assertEquals(25, logs.single().actualMinutes)
        assertTrue(logs.single().completed)
        assertNull(repo.running())
    }

    @Test
    fun extend_thenDone_logsExtendedTotal() = runTest {
        val s = startTask()
        advance(20 * MIN)
        val extended = repo.extend(s.id)!!
        assertEquals(10, extended.extendedMinutes)
        advance(15 * MIN)
        val ended = repo.finish(s.id, FocusOutcome.DONE, endedOnTime = true)!!
        assertEquals(FocusOutcome.EXTENDED, ended.outcome)
        val log = db.focusDao().allLogs().single()
        assertEquals(25, log.plannedMinutes)
        assertEquals(35, log.actualMinutes)
    }

    @Test
    fun drop_logsIncompleteAtTheRealTime() = runTest {
        val s = startTask()
        advance(8 * MIN)
        val ended = repo.finish(s.id, FocusOutcome.DROPPED, endedOnTime = false)!!
        assertEquals(FocusOutcome.DROPPED, ended.outcome)
        val log = db.focusDao().allLogs().single()
        assertEquals(8, log.actualMinutes)
        assertFalse(log.completed)
    }

    @Test
    fun extend_afterEnd_doesNothing() = runTest {
        val s = startTask()
        repo.finish(s.id, FocusOutcome.DONE, endedOnTime = false)
        assertNull(repo.extend(s.id))
    }

    @Test
    fun recover_afterRebootMidSession_resumesOnNewBoot() = runTest {
        val s = startTask()
        now = ClockReading(now.wallMillis + 10 * MIN, elapsedMillis = 20_000, bootCount = 4)
        val recovered = repo.recover()
        assertTrue(recovered is Recovered.Resumed)
        val stored = repo.find(s.id)!!
        assertEquals(4, stored.bootCount)
        assertEquals(15 * MIN, FocusTiming.remainingMillis(stored, now))
    }

    @Test
    fun recover_afterRebootPastTheEnd_closesAsDoneWithLog() = runTest {
        val s = startTask()
        now = ClockReading(now.wallMillis + 2 * 60 * MIN, elapsedMillis = 20_000, bootCount = 4)
        val recovered = repo.recover()
        assertTrue(recovered is Recovered.Closed)
        val closed = (recovered as Recovered.Closed).session
        assertEquals(FocusOutcome.DONE, closed.outcome)
        assertEquals("ends at the planned time, not when we noticed", s.plannedEndEpochMillis, closed.actualEndEpochMillis)
        assertEquals(1, db.focusDao().allLogs().size)
        assertNull(repo.recover())
    }

    @Test
    fun nextStep_onTaskSession_goesToTask() = runTest {
        val s = startTask()
        repo.finish(s.id, FocusOutcome.DONE, endedOnTime = false)
        repo.saveNextStep(s, "  Add captions to the second half ")
        assertEquals("Add captions to the second half", db.taskDao().findById(taskId)?.nextStep)
        assertEquals("Add captions to the second half", repo.currentNextStep(s))
        repo.saveNextStep(s, " ")
        assertNull("blank clears it", db.taskDao().findById(taskId)?.nextStep)
    }

    @Test
    fun nextStep_onBlockSession_staysOnTheSession() = runTest {
        val s = (repo.start(null, "DBMS lecture", roleId, 50) as StartResult.Started).session
        repo.saveNextStep(s, "Revise normal forms")
        assertEquals("Revise normal forms", repo.find(s.id)?.nextStep)
    }

    @Test
    fun deletingTheTask_keepsTheSessionHistory() = runTest {
        val s = startTask()
        db.taskDao().deleteById(taskId)
        val stored = repo.find(s.id)!!
        assertNull(stored.taskId)
        assertEquals(roleId, stored.roleId)
    }

    private companion object {
        const val MIN = 60_000L
    }
}
