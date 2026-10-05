package com.kairo.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.local.Role
import com.kairo.app.data.repository.FocusRepository
import com.kairo.app.data.repository.RoleRepository
import com.kairo.app.service.focus.FocusEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FocusMode { SESSION, NEXT_STEP }

data class FocusUiState(val session: FocusSession? = null, val role: Role? = null, val loaded: Boolean = false)

class FocusViewModel(
    private val sessionId: Long,
    private val repository: FocusRepository,
    private val engine: FocusEngine,
    roles: RoleRepository,
) : ViewModel() {

    val state: StateFlow<FocusUiState> = combine(repository.sessionFlow(sessionId), roles.allRoles()) { session, allRoles ->
        FocusUiState(session, allRoles.firstOrNull { it.id == session?.roleId }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    private val _nextStepDraft = MutableStateFlow<String?>(null)

    /** Pre-filled with the current next step once, so the user edits rather than retypes. */
    val nextStepDraft: StateFlow<String?> = _nextStepDraft.asStateFlow()

    fun loadDraft() {
        if (_nextStepDraft.value != null) return
        viewModelScope.launch {
            val session = repository.find(sessionId) ?: return@launch
            _nextStepDraft.value = repository.currentNextStep(session).orEmpty()
        }
    }

    fun onDraftChange(text: String) {
        _nextStepDraft.value = text
    }

    fun extend() = viewModelScope.launch { engine.extend(sessionId) }

    /** In-app Done: no notification needed, the screen switches straight to the next step. */
    fun done(onFinished: () -> Unit) = viewModelScope.launch {
        engine.finish(sessionId, FocusOutcome.DONE, reason = null)
        onFinished()
    }

    fun drop(onFinished: () -> Unit) = viewModelScope.launch {
        engine.finish(sessionId, FocusOutcome.DROPPED, reason = null)
        onFinished()
    }

    fun saveNextStep(onSaved: () -> Unit) = viewModelScope.launch {
        val session = repository.find(sessionId) ?: return@launch onSaved()
        engine.saveNextStep(session, _nextStepDraft.value.orEmpty())
        onSaved()
    }

    fun skipNextStep(onSkipped: () -> Unit) = viewModelScope.launch {
        engine.dismissEndedNotification()
        onSkipped()
    }

    /**
     * Opened from the ongoing notification after the session already ended: go to the next step.
     * Null means there's nothing to show (a dropped session), so the screen closes.
     */
    fun modeFor(requested: FocusMode, session: FocusSession?): FocusMode? = when {
        session == null || session.outcome == FocusOutcome.RUNNING -> requested
        session.outcome == FocusOutcome.DROPPED -> if (requested == FocusMode.NEXT_STEP) requested else null
        else -> FocusMode.NEXT_STEP
    }
}
