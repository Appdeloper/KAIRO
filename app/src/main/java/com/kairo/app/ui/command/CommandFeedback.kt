package com.kairo.app.ui.command

import com.kairo.app.util.beta.BetaEvent
import com.kairo.app.util.beta.Events
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.kairo.app.R
import com.kairo.app.ai.ParseNotice
import com.kairo.app.ai.UnclearReason
import com.kairo.app.domain.plan.AppliedDiff
import kotlinx.coroutines.flow.Flow

/** Snackbar texts resolved up front, because showSnackbar runs outside composition. */
private data class CommandMessages(
    val applied: String,
    val undo: String,
    val notUnderstood: String,
    val stale: String,
    val undone: String,
    val undoFailed: String,
    val aiUnclear: String,
    val lectureFixed: String,
    val quotaFallback: String,
    val aiUnauthorized: String,
)

@Composable
private fun commandMessages() = CommandMessages(
    applied = stringResource(R.string.msg_applied),
    undo = stringResource(R.string.msg_undo),
    notUnderstood = stringResource(R.string.msg_not_understood),
    stale = stringResource(R.string.msg_stale),
    undone = stringResource(R.string.msg_undone),
    undoFailed = stringResource(R.string.msg_undo_failed),
    aiUnclear = stringResource(R.string.msg_ai_unclear),
    lectureFixed = stringResource(R.string.msg_ai_lecture_fixed),
    quotaFallback = stringResource(R.string.msg_ai_quota),
    aiUnauthorized = stringResource(R.string.msg_ai_unauthorized),
)

/** Turns command outcomes into snackbars (with Undo) the same way on every screen. */
@Composable
fun CommandFeedbackEffect(
    events: Flow<CommandEvent>,
    snackbar: SnackbarHostState,
    onUndo: (AppliedDiff) -> Unit,
    onApplied: () -> Unit = {},
) {
    val messages = commandMessages()
    val undo by rememberUpdatedState(onUndo)
    val applied by rememberUpdatedState(onApplied)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is CommandEvent.Applied -> {
                    Events.record(BetaEvent.PLAN_APPLIED)
                    applied()
                    val result = snackbar.showSnackbar(messages.applied, actionLabel = messages.undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) {
                        Events.record(BetaEvent.PLAN_UNDONE)
                        undo(event.applied)
                    }
                }
                CommandEvent.NotUnderstood -> snackbar.showSnackbar(messages.notUnderstood)
                is CommandEvent.AiUnclear -> snackbar.showSnackbar(
                    if (event.reason == UnclearReason.LECTURE_FIXED) messages.lectureFixed else messages.aiUnclear,
                )
                is CommandEvent.Notice -> snackbar.showSnackbar(
                    when (val notice = event.notice) {
                        is ParseNotice.QuotaExceeded -> notice.message ?: messages.quotaFallback
                        ParseNotice.Unauthorized -> messages.aiUnauthorized
                    },
                )
                CommandEvent.Stale -> snackbar.showSnackbar(messages.stale)
                CommandEvent.Undone -> snackbar.showSnackbar(messages.undone)
                CommandEvent.UndoFailed -> snackbar.showSnackbar(messages.undoFailed)
            }
        }
    }
}
