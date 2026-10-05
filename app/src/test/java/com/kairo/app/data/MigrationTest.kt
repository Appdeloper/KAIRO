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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
