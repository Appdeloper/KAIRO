package com.kairo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Role::class, FixedBlock::class, Task::class, Note::class, Reminder::class],
    version = 1,
    exportSchema = true,
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun roleDao(): RoleDao
    abstract fun fixedBlockDao(): FixedBlockDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun build(context: Context): KairoDatabase =
            Room.databaseBuilder(context, KairoDatabase::class.java, "kairo.db").build()
    }
}
