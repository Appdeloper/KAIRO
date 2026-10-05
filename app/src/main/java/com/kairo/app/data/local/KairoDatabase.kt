package com.kairo.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Role::class, FixedBlock::class, Task::class, Note::class, Reminder::class, BlockSkip::class],
    version = 2,
    exportSchema = true,
    // v2 only adds the block_skips table, which Room can migrate without hand-written SQL.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun roleDao(): RoleDao
    abstract fun fixedBlockDao(): FixedBlockDao
    abstract fun taskDao(): TaskDao
    abstract fun blockSkipDao(): BlockSkipDao

    companion object {
        fun build(context: Context): KairoDatabase =
            Room.databaseBuilder(context, KairoDatabase::class.java, "kairo.db").build()
    }
}
