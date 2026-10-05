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

/**
 * A one-day exception to a weekly FixedBlock ("skip gym today"). The block itself is never edited,
 * so skipping can't accidentally change the timetable for other weeks.
 */
@Entity(
    tableName = "block_skips",
    primaryKeys = ["blockId", "epochDay"],
    foreignKeys = [
        ForeignKey(entity = FixedBlock::class, parentColumns = ["id"], childColumns = ["blockId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BlockSkip(
    val blockId: Long,
    val epochDay: Long,
)

enum class AlarmType { WAKE, BLOCK, ONE_SHOT }

/**
 * A user alarm. Times are wall-clock (hour/minute in the device's current zone), so a trip across
 * time zones keeps "7:00 wake-up" at 7:00 local. daysOfWeekMask: bit 0 = Monday … bit 6 = Sunday,
 * 0 = one-shot (rings once at the next hour:minute, then disables itself on dismiss).
 */
@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(entity = FixedBlock::class, parentColumns = ["id"], childColumns = ["linkedBlockId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("linkedBlockId")],
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val daysOfWeekMask: Int = 0,
    val enabled: Boolean = true,
    val type: AlarmType = AlarmType.WAKE,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val rampUpSeconds: Int = 20,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val linkedBlockId: Long? = null,
    val offsetMinutesBeforeBlock: Int? = null,
    val skipNextOnce: Boolean = false,
    /** Local date (epoch day) of the occurrence being skipped; a date survives time-zone changes. */
    val skipDateEpochDay: Long? = null,
    val openBriefingOnDismiss: Boolean = true,
    /** Runtime state, persisted so a reboot mid-snooze still rings. */
    val snoozeCount: Int = 0,
    val snoozedUntilMillis: Long? = null,
)

enum class FocusOutcome { RUNNING, DONE, EXTENDED, DROPPED }

/**
 * One focus session. Wall-clock times are for display, logs and recovery after a reboot; the
 * elapsed-realtime anchor (with the boot it belongs to) keeps the countdown right on the same boot
 * even if the user changes the clock. At most one row is RUNNING (FocusSessionDao.startIfIdle).
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("taskId"), Index("outcome")],
)
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val blockLabel: String,
    /** Copied at start so the log keeps the role even if the task is deleted later. */
    val roleId: Long? = null,
    val startAtEpochMillis: Long,
    val plannedEndEpochMillis: Long,
    val actualEndEpochMillis: Long? = null,
    val outcome: FocusOutcome = FocusOutcome.RUNNING,
    val startElapsedMillis: Long,
    /** Settings.Global.BOOT_COUNT at start (or at the last re-anchor after a reboot). */
    val bootCount: Int,
    /** What the user first picked, before any Extend; FocusLog compares against this. */
    val plannedMinutes: Int,
    val extendedMinutes: Int = 0,
    /** Next step for sessions without a task (a lecture block), so the text isn't lost. */
    val nextStep: String? = null,
)

/** One row per finished session, kept small and task-free for Phase 3 insights. */
@Entity(tableName = "focus_logs")
data class FocusLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hourOfDay: Int,
    val roleId: Long? = null,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean,
)
