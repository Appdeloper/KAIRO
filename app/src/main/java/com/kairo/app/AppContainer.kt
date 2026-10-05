package com.kairo.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.kairo.app.ai.BriefClient
import com.kairo.app.ai.BriefingRepository
import com.kairo.app.ai.CloudParser
import com.kairo.app.ai.CommandParserFacade
import com.kairo.app.ai.LocalCommandParser
import com.kairo.app.ai.PlannerSnapshot
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.prefs.AiSettingsRepository
import com.kairo.app.data.prefs.BriefCacheStore
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.AlarmRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.util.DateProvider
import com.kairo.app.util.SystemDateProvider
import kotlinx.coroutines.flow.first

private val Context.userPrefsStore by preferencesDataStore(name = "user_prefs")

/** Manual DI: one place that owns singletons, so swapping in fakes for tests stays trivial. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { KairoDatabase.build(appContext) }

    val roleRepository by lazy { RoleRepository(database.roleDao()) }
    val timetableRepository by lazy { TimetableRepository(database.fixedBlockDao(), database.blockSkipDao()) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val userPrefsRepository by lazy { UserPrefsRepository(appContext.userPrefsStore) }
    val dateProvider: DateProvider = SystemDateProvider
    val alarmRepository by lazy { AlarmRepository(database.alarmDao(), database.fixedBlockDao(), database.blockSkipDao()) }
    val planRepository by lazy { PlanRepository(database, userPrefsRepository, dateProvider) }

    /** App-wide so the undo history survives leaving and re-entering a screen. */
    val commandExecutor by lazy { CommandExecutor(planRepository) }

    val aiSettingsRepository by lazy { AiSettingsRepository(appContext.userPrefsStore) }
    private val httpClient by lazy { CloudParser.defaultHttpClient() }

    private suspend fun plannerSnapshot() = PlannerSnapshot(planRepository.loadState(), userPrefsRepository.prefs.first().firstName)

    val briefingRepository by lazy {
        BriefingRepository(
            client = BriefClient(httpClient, aiSettingsRepository::current),
            cache = BriefCacheStore(appContext.userPrefsStore),
            settings = aiSettingsRepository::current,
            snapshot = ::plannerSnapshot,
        )
    }

    val commandParser by lazy {
        CommandParserFacade(
            cloud = CloudParser(
                http = httpClient,
                settings = aiSettingsRepository::current,
                snapshot = ::plannerSnapshot,
            ),
            local = LocalCommandParser(planRepository::loadState),
            settings = aiSettingsRepository::current,
        )
    }
}
