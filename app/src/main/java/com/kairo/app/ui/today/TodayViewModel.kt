package com.kairo.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.ai.CommandParserFacade
import com.kairo.app.data.local.Task
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.DayProgress
import com.kairo.app.domain.Greeting
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.plan.AppliedDiff
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.ui.command.CommandController
import com.kairo.app.ui.command.CommandEvent
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

class TodayViewModel(
    private val taskRepository: TaskRepository,
    timetableRepository: TimetableRepository,
    roleRepository: RoleRepository,
    prefsRepository: UserPrefsRepository,
    private val dateProvider: DateProvider,
    private val planRepository: PlanRepository,
    private val executor: CommandExecutor,
    private val parser: CommandParserFacade,
) : ViewModel() {

    private val timeline = DayTimelineSource(taskRepository, timetableRepository, roleRepository, dateProvider)

    val state: StateFlow<TodayUiState> = combine(timeline.today(), prefsRepository.prefs) { day, prefs ->
        TodayUiState(
            firstName = prefs.firstName,
            dayPart = Greeting.dayPartFor(dateProvider.nowMinuteOfDay()),
            date = day.date,
            entries = day.entries,
            progress = DayProgress.of(day.tasks),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val commands = CommandController(viewModelScope, parser, executor, planRepository)
    val pendingDiff: StateFlow<PlanDiff?> = commands.pendingDiff
    val events: Flow<CommandEvent> = commands.events

    fun toggleDone(task: Task) {
        viewModelScope.launch { taskRepository.toggleDone(task) }
    }

    fun submitCommand(text: String) = commands.submit(text)
    fun applyPending() = commands.applyPending()
    fun dismissPending() = commands.dismissPending()
    fun undo(applied: AppliedDiff) = commands.undo(applied)
}
