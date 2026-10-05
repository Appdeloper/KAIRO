package com.kairo.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.data.local.BlockSkip
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.domain.plan.ApplyResult
import com.kairo.app.domain.plan.Change
import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.domain.plan.TargetRef
import com.kairo.app.domain.plan.UndoResult
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate

/** apply/undo against a real Room database, so the single-transaction guarantee is actually exercised. */
@RunWith(AndroidJUnit4::class)
class CommandExecutorRoomTest {
    @get:Rule val tmp = TemporaryFolder()

    private val monday = LocalDate.of(2026, 10, 5)
    private lateinit var db: KairoDatabase
    private lateinit var repo: PlanRepository
    private lateinit var executor: CommandExecutor
    private var collegeId = 0L

    private data class Snapshot(val tasks: List<Task>, val skips: List<BlockSkip>, val blocks: List<FixedBlock>)

    private suspend fun snapshot() = Snapshot(db.taskDao().allTasksOnce(), db.blockSkipDao().allOnce(), db.fixedBlockDao().allBlocksOnce())

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KairoDatabase::class.java).build()
        RoleRepository(db.roleDao()).seedDefaultsIfEmpty()
        collegeId = db.roleDao().allRoles().first().first().id
        val dates = object : DateProvider {
            override fun today() = monday
            override fun nowMinuteOfDay() = 8 * 60
            override fun todayFlow() = flowOf(monday)
        }
        val prefs = UserPrefsRepository(PreferenceDataStoreFactory.create { tmp.newFile("prefs.preferences_pb") })
        repo = PlanRepository(db, prefs, dates) { 42L }
        executor = CommandExecutor(repo)

        db.fixedBlockDao().upsert(FixedBlock(title = "DBMS lecture", roleId = collegeId, dayOfWeek = 1, startMinute = 15 * 60, endMinute = 16 * 60))
        db.taskDao().insert(Task(title = "Gym", roleId = collegeId, durationMinutes = 60, status = TaskStatus.SCHEDULED,
            scheduledEpochDay = monday.toEpochDay(), scheduledStartMinute = 18 * 60, createdAt = 1))
        db.taskDao().insert(Task(title = "Invoice", roleId = collegeId, durationMinutes = 15, createdAt = 2))
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun planAndApply(command: Command): ApplyResult {
        val diff = executor.plan(command, repo.loadState())
        assertTrue("$command should produce a change", diff.canApply)
        return executor.apply(diff)
    }

    @Test
    fun undo_restoresExactPreviousStateAcrossFiveSteps() = runBlocking {
        val original = snapshot()
        val applied = listOf(
            Command.AddTask("Call", date = monday.plusDays(1), startMinute = 17 * 60),
            Command.SkipBlock(TargetRef.ByName("dbms")),
            Command.CompleteTask(TargetRef.ByName("invoice")),
            Command.MoveBlock(TargetRef.ByName("gym"), toStartMinute = 19 * 60),
            Command.BrainDump(listOf(Command.AddTask("Reel"), Command.AddTask("Notes"))),
        ).map { (planAndApply(it) as ApplyResult.Applied).applied }

        val changed = snapshot()
        assertNotEquals(original, changed)
        assertEquals(5, changed.tasks.size)
        assertEquals(1, changed.skips.size)
        assertEquals(5, executor.undoDepth)

        for (step in applied.asReversed()) assertEquals(UndoResult.Undone, executor.undo(step))
        assertEquals(original, snapshot())
        assertEquals(0, executor.undoDepth)
    }

    @Test
    fun undo_isLastInFirstOut() = runBlocking {
        val first = (planAndApply(Command.AddTask("A", startMinute = 10 * 60)) as ApplyResult.Applied).applied
        val second = (planAndApply(Command.AddTask("B", startMinute = 11 * 60)) as ApplyResult.Applied).applied
        assertEquals(UndoResult.NotLatest, executor.undo(first))
        assertEquals(UndoResult.Undone, executor.undo(second))
        assertEquals(UndoResult.Undone, executor.undo(first))
    }

    @Test
    fun history_keepsOnlyLastFive() = runBlocking {
        repeat(6) { planAndApply(Command.AddTask("T$it", startMinute = (9 + it) * 60)) }
        assertEquals(5, executor.undoDepth)
    }

    @Test
    fun apply_staleDiffWritesNothingEvenIfEarlierChangesWereFine() = runBlocking {
        val state = repo.loadState()
        val gym = state.tasks.single { it.title == "Gym" }
        val newTask = (executor.plan(Command.AddTask("New", startMinute = 10 * 60), state).mutations.single() as Change.Added).task
        val moveGym = executor.plan(Command.MoveBlock(TargetRef.ByName("gym"), toStartMinute = 20 * 60), state).mutations.single()
        val diff = PlanDiff(listOf(Change.Added(newTask), moveGym))

        // Someone edits Gym between preview and Apply.
        db.taskDao().update(gym.copy(title = "Gym (edited)"))
        val before = snapshot()

        assertEquals(ApplyResult.Stale, executor.apply(diff))
        assertEquals("the Added before the stale Moved must be rolled back", before, snapshot())
        assertEquals(0, executor.undoDepth)
    }

    @Test
    fun undo_refusesWhenTheItemChangedSince() = runBlocking {
        val applied = (planAndApply(Command.CompleteTask(TargetRef.ByName("invoice"))) as ApplyResult.Applied).applied
        val invoice = db.taskDao().allTasksOnce().single { it.title == "Invoice" }
        db.taskDao().update(invoice.copy(status = TaskStatus.TODO))
        val before = snapshot()
        assertEquals(UndoResult.Stale, executor.undo(applied))
        assertEquals(before, snapshot())
    }

    @Test
    fun theLectureRowIsNeverWritten() = runBlocking {
        val lectureBefore = db.fixedBlockDao().allBlocksOnce().single()
        planAndApply(Command.SkipBlock(TargetRef.ByName("dbms lecture")))
        planAndApply(Command.AddTask("Call", startMinute = 15 * 60))
        val moveLecture = executor.plan(Command.MoveBlock(TargetRef.ByName("dbms lecture"), toStartMinute = 18 * 60), repo.loadState())
        assertEquals(ApplyResult.NothingToApply, executor.apply(moveLecture))
        assertEquals(lectureBefore, db.fixedBlockDao().allBlocksOnce().single())
    }
}
