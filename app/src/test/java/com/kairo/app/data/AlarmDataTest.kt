package com.kairo.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.local.AlarmType
import com.kairo.app.data.local.BlockSkip
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.AlarmRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.domain.plan.Change
import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.Reason
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate

/** Alarms in Room, their block links, and the wiring into PlanState that keeps tasks off alarm times. */
@RunWith(AndroidJUnit4::class)
class AlarmDataTest {
    @get:Rule val tmp = TemporaryFolder()

    private val monday = LocalDate.of(2026, 10, 5)
    private lateinit var db: KairoDatabase
    private lateinit var alarms: AlarmRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KairoDatabase::class.java).build()
        RoleRepository(db.roleDao()).seedDefaultsIfEmpty()
        alarms = AlarmRepository(db.alarmDao(), db.fixedBlockDao(), db.blockSkipDao())
        Unit
    }

    @After
    fun tearDown() = db.close()

    private fun planRepository() = PlanRepository(
        db,
        UserPrefsRepository(PreferenceDataStoreFactory.create { tmp.newFile("p.preferences_pb") }),
        object : DateProvider {
            override fun today() = monday
            override fun nowMinuteOfDay() = 8 * 60
            override fun todayFlow() = flowOf(monday)
        },
    )

    @Test
    fun crudAndFlow() = runBlocking {
        val id = alarms.save(Alarm(label = "Wake", hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS))
        assertEquals("Wake", alarms.alarms().first().single().label)
        alarms.save(alarms.find(id)!!.copy(label = "Uth ja"))
        assertEquals("Uth ja", alarms.find(id)!!.label)
        alarms.delete(alarms.find(id)!!)
        assertNull(alarms.find(id))
    }

    @Test
    fun blockAlarmFollowsItsLectureAndSurvivesItsDeletion() = runBlocking {
        val roleId = db.roleDao().allRoles().first().first().id
        val blockId = db.fixedBlockDao().upsert(FixedBlock(title = "DBMS", roleId = roleId, dayOfWeek = 3, startMinute = 9 * 60, endMinute = 10 * 60))
        val id = alarms.save(Alarm(hour = 8, minute = 30, type = AlarmType.BLOCK, linkedBlockId = blockId, offsetMinutesBeforeBlock = 30, daysOfWeekMask = AlarmDays.bit(java.time.DayOfWeek.WEDNESDAY)))
        db.blockSkipDao().insert(BlockSkip(blockId, monday.plusDays(2).toEpochDay()))

        val plan = alarms.plansOnce().single()
        assertEquals(8 * 60 + 30, plan.minuteOfDay)
        assertEquals(setOf(monday.plusDays(2).toEpochDay()), plan.skipEpochDays)

        db.fixedBlockDao().delete(db.fixedBlockDao().allBlocksOnce().single())
        assertNull("link cleared, alarm kept", alarms.find(id)!!.linkedBlockId)
    }

    @Test
    fun enabledAlarmsReachPlanState_andTheSchedulerAvoidsThem() = runBlocking {
        alarms.save(Alarm(label = "Gym alarm", hour = 9, minute = 0, daysOfWeekMask = AlarmDays.EVERY_DAY))
        alarms.save(Alarm(label = "Off", hour = 10, minute = 0, daysOfWeekMask = AlarmDays.EVERY_DAY, enabled = false))
        val repo = planRepository()
        val state = repo.loadState()
        assertEquals(listOf(9 * 60), state.alarmsOn(monday))
        assertEquals(listOf(9 * 60), state.alarmsOn(monday.plusDays(13)))

        val diff = CommandExecutor(repo).plan(Command.AddTask("Report", startMinute = 8 * 60 + 30, durationMinutes = 60), state)
        val placed = (diff.mutations.single() as Change.Added).task
        assertFalse(9 * 60 in placed.scheduledStartMinute!! until placed.scheduledStartMinute!! + 60)
        assertTrue(diff.warnings.single() is Reason.ShiftedAroundFixed)
        assertNull("an alarm obstacle has no block title", (diff.warnings.single() as Reason.ShiftedAroundFixed).obstacleTitle)
    }
}
