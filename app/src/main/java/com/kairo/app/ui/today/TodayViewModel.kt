package com.kairo.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Task
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.DayProgress
import com.kairo.app.domain.Greeting
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.parser.LocalParser
import com.kairo.app.domain.parser.ParseContext
import com.kairo.app.domain.plan.AppliedDiff
import com.kairo.app.domain.plan.ApplyResult
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.domain.plan.UndoResult
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
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

/** One-shot outcomes shown as snackbars. */
sealed interface CommandEvent {
    data object NotUnderstood : CommandEvent
    data class Applied(val applied: AppliedDiff) : CommandEvent
    data object Stale : CommandEvent
    data object Undone : CommandEvent
    data object UndoFailed : CommandEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val taskRepository: TaskRepository,
    timetableRepository: TimetableRepository,
    roleRepository: RoleRepository,
    prefsRepository: UserPrefsRepository,
    private val dateProvider: DateProvider,
    private val planRepository: PlanRepository,
    private val executor: CommandExecutor,
) : ViewModel() {

    private data class DayContent(val date: LocalDate, val blocks: List<FixedBlock>, val tasks: List<Task>, val skipped: Set<Long>)

    private val dayContent: Flow<DayContent> = dateProvider.todayFlow().flatMapLatest { date ->
        combine(
            timetableRepository.blocksForDay(date.dayOfWeek.value),
            taskRepository.tasksForDate(date.toEpochDay()),
            timetableRepository.skippedBlockIdsOn(date.toEpochDay()),
        ) { blocks, tasks, skipped -> DayContent(date, blocks, tasks, skipped.toSet()) }
    }

    val state: StateFlow<TodayUiState> = combine(
        dayContent,
        roleRepository.allRoles(),
        prefsRepository.prefs,
    ) { day, roles, prefs ->
        TodayUiState(
            firstName = prefs.firstName,
            dayPart = Greeting.dayPartFor(dateProvider.nowMinuteOfDay()),
            date = day.date,
            entries = TimelineBuilder.build(day.blocks, day.tasks, roles, day.skipped),
            progress = DayProgress.of(day.tasks),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** The diff waiting for Apply/Cancel. Null when the sheet is closed. */
    private val _pendingDiff = MutableStateFlow<PlanDiff?>(null)
    val pendingDiff: StateFlow<PlanDiff?> = _pendingDiff.asStateFlow()

    private val _events = Channel<CommandEvent>(Channel.BUFFERED)
    val events: Flow<CommandEvent> = _events.receiveAsFlow()

    fun toggleDone(task: Task) {
        viewModelScope.launch { taskRepository.toggleDone(task) }
    }

    /** Text -> Command -> PlanDiff preview. Nothing is written until applyPending(). */
    fun submitCommand(text: String) {
        viewModelScope.launch {
            val snapshot = planRepository.loadState()
            val context = ParseContext(snapshot.today, snapshot.nowMinute, snapshot.wakeMinute, snapshot.sleepMinute)
            val command = LocalParser.parse(text, context)
            if (command == null) {
                _events.send(CommandEvent.NotUnderstood)
            } else {
                _pendingDiff.value = executor.plan(command, snapshot)
            }
        }
    }

    fun applyPending() {
        val diff = _pendingDiff.value ?: return
        _pendingDiff.value = null
        viewModelScope.launch {
            when (val result = executor.apply(diff)) {
                is ApplyResult.Applied -> _events.send(CommandEvent.Applied(result.applied))
                ApplyResult.Stale -> _events.send(CommandEvent.Stale)
                ApplyResult.NothingToApply -> Unit
            }
        }
    }

    fun dismissPending() {
        _pendingDiff.value = null
    }

    fun undo(applied: AppliedDiff) {
        viewModelScope.launch {
            val event = if (executor.undo(applied) == UndoResult.Undone) CommandEvent.Undone else CommandEvent.UndoFailed
            _events.send(event)
        }
    }
}
