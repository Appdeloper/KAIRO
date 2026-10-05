package com.kairo.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.kairo.app.data.local.KairoDatabase
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.util.DateProvider
import com.kairo.app.util.SystemDateProvider

private val Context.userPrefsStore by preferencesDataStore(name = "user_prefs")

/** Manual DI: one place that owns singletons, so swapping in fakes for tests stays trivial. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { KairoDatabase.build(appContext) }

    val roleRepository by lazy { RoleRepository(database.roleDao()) }
    val timetableRepository by lazy { TimetableRepository(database.fixedBlockDao()) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val userPrefsRepository by lazy { UserPrefsRepository(appContext.userPrefsStore) }
    val dateProvider: DateProvider = SystemDateProvider
}
