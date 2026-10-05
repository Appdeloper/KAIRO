package com.kairo.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.Task
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.DayProgress
import com.kairo.app.domain.Greeting
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val firstName: String = "",
    val dayPart: DayPart = DayPart.MORNING,
    val date: LocalDate = LocalDate.now(),
    val entries: List<TimelineEntry> = emptyList(),
    val progress: DayProgress = DayProgress(0, 0),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val taskRepository: TaskRepository,
    timetableRepository: TimetableRepository,
    roleRepository: RoleRepository,
    prefsRepository: UserPrefsRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val dayContent = dateProvider.todayFlow().flatMapLatest { date ->
        combine(
            timetableRepository.blocksForDay(date.dayOfWeek.value),
            taskRepository.tasksForDate(date.toEpochDay()),
        ) { blocks, tasks -> Triple(date, blocks, tasks) }
    }

    val state: StateFlow<TodayUiState> = combine(
        dayContent,
        roleRepository.allRoles(),
        prefsRepository.prefs,
    ) { (date, blocks, tasks), roles, prefs ->
        TodayUiState(
            firstName = prefs.firstName,
            dayPart = Greeting.dayPartFor(dateProvider.nowMinuteOfDay()),
            date = date,
            entries = TimelineBuilder.build(blocks, tasks, roles),
            progress = DayProgress.of(tasks),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggleDone(task: Task) {
        viewModelScope.launch { taskRepository.toggleDone(task) }
    }
}
