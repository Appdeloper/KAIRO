package com.kairo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.data.repository.DefaultRoles
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: KairoDatabase
    private var collegeId = 0L
    private var clientId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KairoDatabase::class.java).allowMainThreadQueries().build()
        RoleRepository(db.roleDao()).seedDefaultsIfEmpty()
        val roles = db.roleDao().allRoles().first()
        collegeId = roles.first { it.name == "College" }.id
        clientId = roles.first { it.name == "Client" }.id
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seeding_insertsFourRolesOnce() = runTest {
        val repo = RoleRepository(db.roleDao())
        assertFalse("second seed must be a no-op", repo.seedDefaultsIfEmpty())
        val roles = db.roleDao().allRoles().first()
        assertEquals(DefaultRoles.all.map { it.name }, roles.map { it.name })
        assertEquals("each role gets its own color", 4, roles.map { it.colorHex }.toSet().size)
        assertTrue(roles.all { it.dailyBudgetMinutes > 0 })
    }

    @Test
    fun blocksForDay_returnsOnlyThatDaySortedByStart() = runTest {
        val dao = db.fixedBlockDao()
        dao.upsert(FixedBlock(title = "OS lab", roleId = collegeId, dayOfWeek = 1, startMinute = 660, endMinute = 780))
        dao.upsert(FixedBlock(title = "DBMS", roleId = collegeId, dayOfWeek = 1, startMinute = 540, endMinute = 600))
        dao.upsert(FixedBlock(title = "Maths", roleId = collegeId, dayOfWeek = 3, startMinute = 600, endMinute = 660))

        assertEquals(listOf("DBMS", "OS lab"), dao.blocksForDay(1).first().map { it.title })
        assertEquals(listOf("Maths"), dao.blocksForDay(3).first().map { it.title })
        assertTrue(dao.blocksForDay(7).first().isEmpty())
    }

    @Test
    fun blocksForDay_emitsAgainAfterEditAndDelete() = runTest {
        val dao = db.fixedBlockDao()
        val id = dao.upsert(FixedBlock(title = "DBMS", roleId = collegeId, dayOfWeek = 2, startMinute = 540, endMinute = 600))
        val saved = dao.blocksForDay(2).first().single()
        assertEquals(id, saved.id)

        dao.upsert(saved.copy(title = "DBMS (moved)", dayOfWeek = 4))
        assertTrue(dao.blocksForDay(2).first().isEmpty())
        assertEquals("DBMS (moved)", dao.blocksForDay(4).first().single().title)

        dao.delete(dao.blocksForDay(4).first().single())
        assertTrue(dao.allBlocks().first().isEmpty())
    }

    @Test
    fun tasksForDate_returnsOnlyThatDaySortedByStart() = runTest {
        val dao = db.taskDao()
        dao.insert(task("Late", day = 100, start = 1200))
        dao.insert(task("Early", day = 100, start = 480))
        dao.insert(task("Other day", day = 101, start = 600))
        dao.insert(task("Backlog", day = null, start = null))

        assertEquals(listOf("Early", "Late"), dao.tasksForDate(100).first().map { it.title })
    }

    @Test
    fun unfinishedBefore_excludesDoneDroppedTodayAndUnscheduled() = runTest {
        val dao = db.taskDao()
        dao.insert(task("Yesterday open", day = 99, start = 600))
        dao.insert(task("Yesterday done", day = 99, start = 700, status = TaskStatus.DONE))
        dao.insert(task("Yesterday dropped", day = 99, start = 800, status = TaskStatus.DROPPED))
        dao.insert(task("Today", day = 100, start = 600))
        dao.insert(task("Backlog", day = null, start = null, status = TaskStatus.TODO))

        assertEquals(listOf("Yesterday open"), dao.unfinishedBefore(100).first().map { it.title })
    }

    @Test
    fun unscheduledOpenTasks_ordersByPriorityThenDeadline() = runTest {
        val dao = db.taskDao()
        dao.insert(task("P3", day = null, start = null, priority = 3))
        dao.insert(task("P1 no deadline", day = null, start = null, priority = 1))
        dao.insert(task("P1 due soon", day = null, start = null, priority = 1, deadline = 105))
        dao.insert(task("Done", day = null, start = null, priority = 1, status = TaskStatus.DONE))
        dao.insert(task("Placed", day = 100, start = 600, priority = 1))

        assertEquals(
            listOf("P1 due soon", "P1 no deadline", "P3"),
            dao.unscheduledOpenTasks().first().map { it.title },
        )
    }

    @Test
    fun toggleDone_flipsBetweenScheduledAndDone() = runTest {
        val repo = TaskRepository(db.taskDao()) { 0L }
        val id = repo.addTask("Edit reel", clientId, 30, priority = 2, deadlineEpochDay = null)
        val backlog = repo.unscheduledOpenTasks().first().single { it.id == id }
        repo.placeManually(backlog, epochDay = 100, startMinute = 900)

        val placed = repo.tasksForDate(100).first().single()
        assertEquals(TaskStatus.SCHEDULED, placed.status)
        repo.toggleDone(placed)
        val done = repo.tasksForDate(100).first().single()
        assertEquals(TaskStatus.DONE, done.status)
        repo.toggleDone(done)
        assertEquals(TaskStatus.SCHEDULED, repo.tasksForDate(100).first().single().status)
    }

    private fun task(
        title: String,
        day: Long?,
        start: Int?,
        status: TaskStatus = if (day != null) TaskStatus.SCHEDULED else TaskStatus.TODO,
        priority: Int = 3,
        deadline: Long? = null,
    ) = Task(
        title = title,
        roleId = collegeId,
        durationMinutes = 30,
        priority = priority,
        deadlineEpochDay = deadline,
        status = status,
        scheduledEpochDay = day,
        scheduledStartMinute = start,
        createdAt = 0,
    )
}
