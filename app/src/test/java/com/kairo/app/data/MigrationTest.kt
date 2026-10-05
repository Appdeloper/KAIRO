package com.kairo.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.app.data.local.KairoDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Users upgrade from any earlier step's database; their data must survive every move up to the current version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KairoDatabase::class.java)

    @Test
    fun v1ToV2_keepsDataAndAddsBlockSkips() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO roles (id, name, colorHex, dailyBudgetMinutes) VALUES (1, 'College', '#00E5FF', 360)")
            execSQL("INSERT INTO fixed_blocks (id, title, roleId, dayOfWeek, startMinute, endMinute, location, source) VALUES (1, 'DBMS', 1, 1, 540, 600, NULL, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true)
        db.execSQL("INSERT INTO block_skips (blockId, epochDay) VALUES (1, 20366)")
        db.query("SELECT COUNT(*) FROM fixed_blocks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM block_skips").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun v2ToV3_keepsDataAndAddsAlarms() {
        helper.createDatabase(DB_NAME_V2, 2).apply {
            execSQL("INSERT INTO roles (id, name, colorHex, dailyBudgetMinutes) VALUES (1, 'College', '#00E5FF', 360)")
            execSQL("INSERT INTO fixed_blocks (id, title, roleId, dayOfWeek, startMinute, endMinute, location, source) VALUES (1, 'DBMS', 1, 1, 540, 600, NULL, 'MANUAL')")
            execSQL("INSERT INTO block_skips (blockId, epochDay) VALUES (1, 20366)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME_V2, 3, true)
        db.execSQL(
            "INSERT INTO alarms (id, label, hour, minute, daysOfWeekMask, enabled, type, vibrate, rampUpSeconds, snoozeMinutes, maxSnoozes, " +
                "linkedBlockId, skipNextOnce, openBriefingOnDismiss, snoozeCount) VALUES (1, 'Wake', 7, 0, 31, 1, 'WAKE', 1, 20, 5, 3, 1, 0, 1, 0)",
        )
        db.query("SELECT COUNT(*) FROM block_skips").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT label FROM alarms").use { it.moveToFirst(); assertEquals("Wake", it.getString(0)) }
    }

    @Test
    fun v1ToV3_inOneUpgrade() {
        helper.createDatabase(DB_NAME_V1_TO_V3, 1).close()
        helper.runMigrationsAndValidate(DB_NAME_V1_TO_V3, 3, true).close()
    }

    @Test
    fun v3ToV4_keepsDataAndAddsFocusTables() {
        helper.createDatabase(DB_NAME_V3, 3).apply {
            execSQL("INSERT INTO roles (id, name, colorHex, dailyBudgetMinutes) VALUES (1, 'Content', '#FF2E93', 60)")
            execSQL("INSERT INTO tasks (id, title, roleId, durationMinutes, priority, status, nextStep, createdAt) VALUES (1, 'Reel', 1, 45, 3, 'TODO', NULL, 0)")
            execSQL(
                "INSERT INTO alarms (id, label, hour, minute, daysOfWeekMask, enabled, type, vibrate, rampUpSeconds, snoozeMinutes, maxSnoozes, " +
                    "skipNextOnce, openBriefingOnDismiss, snoozeCount) VALUES (1, 'Wake', 7, 0, 31, 1, 'WAKE', 1, 20, 5, 3, 0, 1, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME_V3, 4, true)
        db.execSQL(
            "INSERT INTO focus_sessions (id, taskId, blockLabel, roleId, startAtEpochMillis, plannedEndEpochMillis, outcome, " +
                "startElapsedMillis, bootCount, plannedMinutes, extendedMinutes) VALUES (1, 1, 'Reel', 1, 0, 1500000, 'RUNNING', 10, 3, 25, 0)",
        )
        db.execSQL("INSERT INTO focus_logs (hourOfDay, roleId, plannedMinutes, actualMinutes, completed) VALUES (16, 1, 25, 25, 1)")
        db.query("SELECT label FROM alarms").use { it.moveToFirst(); assertEquals("Wake", it.getString(0)) }
        db.query("SELECT title FROM tasks").use { it.moveToFirst(); assertEquals("Reel", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM focus_sessions WHERE outcome = 'RUNNING'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun v1ToV4_inOneUpgrade() {
        helper.createDatabase(DB_NAME_V1_TO_V4, 1).close()
        helper.runMigrationsAndValidate(DB_NAME_V1_TO_V4, 4, true).close()
    }

    private companion object {
        const val DB_NAME_V3 = "migration-test-v3"
        const val DB_NAME_V1_TO_V4 = "migration-test-v1-v4"
        const val DB_NAME_V2 = "migration-test-v2"
        const val DB_NAME_V1_TO_V3 = "migration-test-v1-v3"
        const val DB_NAME = "migration-test"
    }
}
