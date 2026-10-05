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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val backlog: List<Task> = emptyList(),
    val carriedOver: List<Task> = emptyList(),
    val roles: List<Role> = emptyList(),
)

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
) : ViewModel() {

    private val carriedOver = dateProvider.todayFlow().flatMapLatest { repository.unfinishedBefore(it.toEpochDay()) }

    val state: StateFlow<TasksUiState> = combine(
        repository.unscheduledOpenTasks(),
        carriedOver,
        roleRepository.allRoles(),
    ) { backlog, old, roles -> TasksUiState(backlog, old, roles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

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
