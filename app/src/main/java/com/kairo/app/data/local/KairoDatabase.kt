package com.kairo.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Role::class, FixedBlock::class, Task::class, Note::class, Reminder::class, BlockSkip::class, Alarm::class],
    version = 3,
    exportSchema = true,
    // v2 adds block_skips, v3 adds alarms: new tables only, so Room migrates without hand-written SQL.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun roleDao(): RoleDao
    abstract fun fixedBlockDao(): FixedBlockDao
    abstract fun taskDao(): TaskDao
    abstract fun blockSkipDao(): BlockSkipDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        fun build(context: Context): KairoDatabase =
            Room.databaseBuilder(context, KairoDatabase::class.java, "kairo.db").build()
    }
}
