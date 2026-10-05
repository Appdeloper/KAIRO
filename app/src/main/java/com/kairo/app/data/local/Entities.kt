package com.kairo.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A life area the user splits time between. The budget is a soft daily cap used later by the scheduler. */
@Entity(tableName = "roles")
data class Role(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val dailyBudgetMinutes: Int,
)

enum class BlockSource { MANUAL, CALENDAR }

/**
 * A weekly-repeating immovable slot (lecture, lab). dayOfWeek follows ISO-8601 / java.time:
 * 1 = Monday … 7 = Sunday. Minutes are counted from local midnight.
 */
@Entity(
    tableName = "fixed_blocks",
    foreignKeys = [
        ForeignKey(entity = Role::class, parentColumns = ["id"], childColumns = ["roleId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("roleId"), Index("dayOfWeek")],
)
data class FixedBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val roleId: Long,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String? = null,
    val source: BlockSource = BlockSource.MANUAL,
)

enum class TaskStatus { TODO, SCHEDULED, DONE, DROPPED }

/** Dates are stored as epoch days (LocalDate.toEpochDay) so they stay timezone-free. */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = Role::class, parentColumns = ["id"], childColumns = ["roleId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("roleId"), Index("scheduledEpochDay"), Index("status")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val roleId: Long,
    val durationMinutes: Int,
    val deadlineEpochDay: Long? = null,
    /** 1 = highest, 4 = lowest. */
    val priority: Int = 3,
    val status: TaskStatus = TaskStatus.TODO,
    val scheduledEpochDay: Long? = null,
    val scheduledStartMinute: Int? = null,
    val nextStep: String? = null,
    val ifThenPlan: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("taskId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val taskId: Long? = null,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("taskId"), Index("triggerAtEpochMillis")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val triggerAtEpochMillis: Long,
    val delivered: Boolean = false,
)
