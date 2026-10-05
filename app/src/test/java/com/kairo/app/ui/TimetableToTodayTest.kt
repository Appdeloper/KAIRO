package com.kairo.app.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.ai.AiSettings
import com.kairo.app.ai.CommandParserFacade
import com.kairo.app.ai.FailReason
import com.kairo.app.ai.LocalCommandParser
import com.kairo.app.ai.ParseResult
import com.kairo.app.data.repository.AlarmRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.ui.timetable.TimetableViewModel
import com.kairo.app.ui.today.TodayViewModel
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate

/** Acceptance check: a lecture saved in Timetable shows up on Today for that weekday, and only that weekday. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TimetableToTodayTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KairoDatabase
    private val monday = LocalDate.of(2026, 10, 5)

    private fun fixedDate(date: LocalDate) = object : DateProvider {
        override fun today() = date
        override fun nowMinuteOfDay() = 8 * 60
        override fun todayFlow() = flowOf(date)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KairoDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun todayViewModel(date: LocalDate) = TodayViewModel(
        TaskRepository(db.taskDao()),
        TimetableRepository(db.fixedBlockDao(), db.blockSkipDao()),
        RoleRepository(db.roleDao()),
        UserPrefsRepository(PreferenceDataStoreFactory.create { tmp.newFile("p${date}.preferences_pb") }),
        fixedDate(date),
        PlanRepository(db, prefsFor(date), fixedDate(date)),
        CommandExecutor(PlanRepository(db, prefsFor(date), fixedDate(date))),
        CommandParserFacade(
            cloud = { ParseResult.Failed(FailReason.NotConfigured) },
            local = LocalCommandParser { PlanRepository(db, prefsFor(date), fixedDate(date)).loadState() },
            settings = { AiSettings() },
        ),
        AlarmRepository(db.alarmDao(), db.fixedBlockDao(), db.blockSkipDao()),
    )

    private fun prefsFor(date: LocalDate) = UserPrefsRepository(PreferenceDataStoreFactory.create { tmp.newFile("q$date.preferences_pb") })

    @Test
    fun lectureAddedInTimetableAppearsOnTodayForThatWeekday() = runBlocking {
        val roles = RoleRepository(db.roleDao())
        roles.seedDefaultsIfEmpty()
        val collegeId = db.roleDao().allRoles().first().first().id
        val timetable = TimetableViewModel(TimetableRepository(db.fixedBlockDao(), db.blockSkipDao()), roles)
        val mondayView = todayViewModel(monday)
        val tuesdayView = todayViewModel(monday.plusDays(1))

        timetable.save(FixedBlock(title = "DBMS lecture", roleId = collegeId, dayOfWeek = 1, startMinute = 540, endMinute = 600))

        val mondayState = withTimeout(5_000) { mondayView.state.first { it.entries.isNotEmpty() } }
        val entry = mondayState.entries.single() as TimelineEntry.Block
        assertEquals("DBMS lecture", entry.title)
        assertEquals("College", entry.role?.name)

        val tuesdayState = withTimeout(5_000) { tuesdayView.state.first { it.date == monday.plusDays(1) } }
        assertEquals(emptyList<TimelineEntry>(), tuesdayState.entries)
    }
}
