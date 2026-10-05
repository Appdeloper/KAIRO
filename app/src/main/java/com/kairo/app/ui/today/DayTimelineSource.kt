package com.kairo.app.ui.today

import com.kairo.app.data.local.Task
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

data class DayTimeline(val date: LocalDate, val entries: List<TimelineEntry>, val tasks: List<Task>)

/** Today's merged lectures + tasks as a live flow; shared by Today, the briefing and the widget. */
class DayTimelineSource(
    private val tasks: TaskRepository,
    private val timetable: TimetableRepository,
    private val roles: RoleRepository,
    private val dates: DateProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun today(): Flow<DayTimeline> = dates.todayFlow().flatMapLatest { date ->
        combine(
            timetable.blocksForDay(date.dayOfWeek.value),
            tasks.tasksForDate(date.toEpochDay()),
            timetable.skippedBlockIdsOn(date.toEpochDay()),
            roles.allRoles(),
        ) { blocks, dayTasks, skipped, allRoles ->
            DayTimeline(date, TimelineBuilder.build(blocks, dayTasks, allRoles, skipped.toSet()), dayTasks)
        }
    }
}
