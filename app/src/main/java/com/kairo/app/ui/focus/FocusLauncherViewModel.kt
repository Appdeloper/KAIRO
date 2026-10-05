package com.kairo.app.ui.focus

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.prefs.FocusPrefsRepository
import com.kairo.app.data.repository.FocusRepository
import com.kairo.app.data.repository.FocusRepository.StartResult
import com.kairo.app.domain.focus.FocusDurations
import com.kairo.app.domain.focus.StartCheck
import com.kairo.app.service.focus.FocusEngine
import com.kairo.app.ui.containerFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun focusLauncherFactory() = containerFactory { FocusLauncherViewModel(it.focusRepository, it.focusPrefsRepository, it.focusEngine) }

/** Today's side of focus: the running chip and the start sheet. */
class FocusLauncherViewModel(
    repository: FocusRepository,
    prefs: FocusPrefsRepository,
    private val engine: FocusEngine,
) : ViewModel() {
    val running: StateFlow<FocusSession?> = repository.runningFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val defaultMinutes: StateFlow<Int> = prefs.settings.map { it.defaultMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusDurations.DEFAULT_MINUTES)

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun start(target: FocusTarget, minutes: Int) = viewModelScope.launch {
        when (val result = engine.start(target.taskId, target.title, target.role?.id, minutes)) {
            is StartResult.Started -> _messages.send(R.string.focus_started)
            is StartResult.Refused -> _messages.send(messageFor(result.reason))
        }
    }

    @StringRes
    private fun messageFor(reason: StartCheck): Int = when (reason) {
        is StartCheck.AlreadyRunning -> R.string.focus_refused_running
        StartCheck.InvalidDuration -> R.string.focus_refused_duration
        StartCheck.NotFromVisibleUi, StartCheck.Ok -> R.string.focus_refused_generic
    }
}
