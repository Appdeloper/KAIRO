package com.kairo.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.app.data.local.KairoDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Users who installed step 1.1 have a v1 database; their data must survive the move to v2. */
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

    private companion object {
        const val DB_NAME_V2 = "migration-test-v2"
        const val DB_NAME_V1_TO_V3 = "migration-test-v1-v3"
        const val DB_NAME = "migration-test"
    }
}
