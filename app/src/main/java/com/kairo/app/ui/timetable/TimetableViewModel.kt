package com.kairo.app.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.FixedBlock
import com.kairo.app.data.local.Role
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.data.repository.TimetableRepository
import com.kairo.app.domain.BlockValidation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimetableUiState(
    val blocks: List<FixedBlock> = emptyList(),
    val roles: List<Role> = emptyList(),
    val hiddenRoleIds: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

class TimetableViewModel(
    private val repository: TimetableRepository,
    roleRepository: RoleRepository,
    hiddenRoleIds: Flow<Set<Long>> = flowOf(emptySet()),
) : ViewModel() {

    val state: StateFlow<TimetableUiState> = combine(repository.allBlocks(), roleRepository.allRoles(), hiddenRoleIds) { blocks, roles, hidden ->
        TimetableUiState(blocks, roles, hidden, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    fun save(block: FixedBlock) {
        // The editor already validates; this guard keeps bad rows out even if a caller skips it.
        if (BlockValidation.validate(block.title, block.dayOfWeek, block.startMinute, block.endMinute) != null) return
        viewModelScope.launch { repository.save(block.copy(title = block.title.trim(), location = block.location?.trim()?.ifBlank { null })) }
    }

    fun delete(block: FixedBlock) {
        viewModelScope.launch { repository.delete(block) }
    }
}
