package com.kairo.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.Task
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.kairo.app.data.local.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val backlog: List<Task> = emptyList(),
    val carriedOver: List<Task> = emptyList(),
    val roles: List<Role> = emptyList(),
    val today: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val done: List<Task> = emptyList(),
    val hiddenRoleIds: Set<Long> = emptySet(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = backlog.isEmpty() && carriedOver.isEmpty() && today.isEmpty() && upcoming.isEmpty() && done.isEmpty()
}

data class NewTaskInput(
    val title: String,
    val roleId: Long,
    val durationMinutes: Int,
    val priority: Int,
    val deadlineEpochDay: Long?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repository: TaskRepository,
    roleRepository: RoleRepository,
    private val dateProvider: DateProvider,
    hiddenRoleIds: Flow<Set<Long>> = flowOf(emptySet()),
) : ViewModel() {

    private val carriedOver = dateProvider.todayFlow().flatMapLatest { repository.unfinishedBefore(it.toEpochDay()) }
    private val todayTasks = dateProvider.todayFlow().flatMapLatest { repository.tasksForDate(it.toEpochDay()) }
    private val upcoming = dateProvider.todayFlow().flatMapLatest { repository.upcomingAfter(it.toEpochDay()) }

    private val lists = combine(repository.unscheduledOpenTasks(), carriedOver, todayTasks, upcoming, repository.recentlyDone()) { backlog, old, today, later, done ->
        TasksUiState(
            backlog = backlog,
            carriedOver = old,
            // Done tasks live in their own group, so Today shows only what's still open.
            today = today.filter { it.status != TaskStatus.DONE && it.status != TaskStatus.DROPPED },
            upcoming = later,
            done = done,
        )
    }

    val state: StateFlow<TasksUiState> = combine(lists, roleRepository.allRoles(), hiddenRoleIds) { l, roles, hidden ->
        l.copy(roles = roles, hiddenRoleIds = hidden, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    /** Swipe-to-drop; [undoDrop] restores the exact previous status. */
    fun drop(task: Task) {
        viewModelScope.launch { repository.setStatus(task, TaskStatus.DROPPED) }
    }

    fun undoDrop(task: Task) {
        viewModelScope.launch { repository.setStatus(task, task.status) }
    }

    fun add(input: NewTaskInput) {
        if (input.title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(input.title, input.roleId, input.durationMinutes, input.priority, input.deadlineEpochDay)
        }
    }

    fun putOnToday(task: Task, startMinute: Int) {
        viewModelScope.launch { repository.placeManually(task, dateProvider.today().toEpochDay(), startMinute) }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { repository.toggleDone(task) }
    }
}
