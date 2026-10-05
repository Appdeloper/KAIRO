package com.kairo.app.ui.command

import com.kairo.app.ai.CommandParserFacade
import com.kairo.app.ai.ParseNotice
import com.kairo.app.ai.ParseResult
import com.kairo.app.ai.ParseSource
import com.kairo.app.ai.UnclearReason
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.domain.plan.AppliedDiff
import com.kairo.app.domain.plan.ApplyResult
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.domain.plan.UndoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot outcomes shown as snackbars. */
sealed interface CommandEvent {
    /** Offline parser didn't recognise the sentence (same as step 1.2). */
    data object NotUnderstood : CommandEvent

    /** The cloud model looked at it and decided it isn't a safe, clear planner command. */
    data class AiUnclear(val reason: UnclearReason) : CommandEvent

    /** Handled offline, but the user should know why the cloud wasn't used. */
    data class Notice(val notice: ParseNotice) : CommandEvent
    data class Applied(val applied: AppliedDiff) : CommandEvent
    data object Stale : CommandEvent
    data object Undone : CommandEvent
    data object UndoFailed : CommandEvent
}

/**
 * Text -> Command(s) -> PlanDiff preview -> Apply/Undo, shared by Today and the briefing screen so
 * both behave identically. Lives in the owning ViewModel's scope.
 */
class CommandController(
    private val scope: CoroutineScope,
    private val parser: CommandParserFacade,
    private val executor: CommandExecutor,
    private val planRepository: PlanRepository,
) {
    /** The diff waiting for Apply/Cancel. Null when the sheet is closed. */
    private val _pendingDiff = MutableStateFlow<PlanDiff?>(null)
    val pendingDiff: StateFlow<PlanDiff?> = _pendingDiff.asStateFlow()

    /** True while a sentence is being parsed and planned (drives the orb's "thinking" state). */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<CommandEvent>(Channel.BUFFERED)
    val events: Flow<CommandEvent> = _events.receiveAsFlow()

    /** Nothing is written until applyPending(). */
    fun submit(text: String) {
        scope.launch {
            _busy.value = true
            try {
                val outcome = parser.parseDetailed(text)
                outcome.notice?.let { _events.send(CommandEvent.Notice(it)) }
                when (val result = outcome.result) {
                    is ParseResult.Parsed -> _pendingDiff.value = executor.planAll(result.commands, planRepository.loadState())
                    is ParseResult.Unclear -> _events.send(
                        if (outcome.source == ParseSource.CLOUD) CommandEvent.AiUnclear(result.reason) else CommandEvent.NotUnderstood,
                    )
                    is ParseResult.Failed -> _events.send(CommandEvent.NotUnderstood)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun applyPending() {
        val diff = _pendingDiff.value ?: return
        _pendingDiff.value = null
        scope.launch {
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
        scope.launch {
            val event = if (executor.undo(applied) == UndoResult.Undone) CommandEvent.Undone else CommandEvent.UndoFailed
            _events.send(event)
        }
    }
}
