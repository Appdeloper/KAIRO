package com.kairo.app.ui.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.ai.BriefingRepository
import com.kairo.app.ai.CommandParserFacade
import com.kairo.app.ai.speech.Transcript
import com.kairo.app.data.local.Task
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.data.prefs.UserPrefsRepository
import com.kairo.app.data.repository.PlanRepository
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.domain.brief.BriefStrings
import com.kairo.app.domain.brief.Upcoming
import com.kairo.app.domain.plan.AppliedDiff
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.PlanDiff
import com.kairo.app.ui.command.CommandController
import com.kairo.app.ui.command.CommandEvent
import com.kairo.app.ui.today.DayTimelineSource
import com.kairo.app.util.DateProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the voice row shows: words streaming in, or a final phrase about to be sent. */
sealed interface TranscriptUi {
    data class Live(val text: String) : TranscriptUi
    data class Confirming(val text: String) : TranscriptUi
}

data class BriefingUiState(
    val brief: Brief? = null,
    val source: BriefSource? = null,
    val briefIsFinal: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val upcoming: List<TimelineEntry> = emptyList(),
    val transcript: TranscriptUi? = null,
)

class BriefingViewModel(
    private val briefing: BriefingRepository,
    private val strings: BriefStrings,
    private val taskRepository: TaskRepository,
    timeline: DayTimelineSource,
    private val prefs: UserPrefsRepository,
    private val dates: DateProvider,
    parser: CommandParserFacade,
    executor: CommandExecutor,
    planRepository: PlanRepository,
) : ViewModel() {

    private val commands = CommandController(viewModelScope, parser, executor, planRepository)
    val pendingDiff: StateFlow<PlanDiff?> = commands.pendingDiff
    val events: Flow<CommandEvent> = commands.events

    private val briefState = MutableStateFlow(BriefingUiState())
    private val transcript = MutableStateFlow<TranscriptUi?>(null)
    private var sendJob: Job? = null
    private var briefJob: Job? = null

    val state: StateFlow<BriefingUiState> = combine(briefState, transcript, timeline.today()) { brief, words, day ->
        brief.copy(
            today = day.date,
            upcoming = Upcoming.remaining(day.entries, dates.nowMinuteOfDay()).take(UPCOMING_COUNT),
            transcript = words,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BriefingUiState())

    val muted: StateFlow<Boolean> = prefs.speechMuted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Bumps each time something worth celebrating happens; the UI plays one particle burst per bump. */
    private val _burst = MutableStateFlow(0)
    val burst: StateFlow<Int> = _burst.asStateFlow()

    private val listening = MutableStateFlow(false)
    private val doneFlash = MutableStateFlow(false)

    val orbState: StateFlow<OrbState> = combine(listening, commands.busy, briefState.map { !it.briefIsFinal }, doneFlash) { mic, busy, loading, done ->
        when {
            mic -> OrbState.LISTENING
            busy || loading -> OrbState.THINKING
            done -> OrbState.DONE
            else -> OrbState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrbState.THINKING)

    init {
        refreshBrief()
    }

    /** Called on open and when the activity is brought back via a tile/widget/shortcut tap. */
    fun refreshBrief() {
        briefJob?.cancel()
        briefJob = viewModelScope.launch {
            briefing.briefs(strings).collect { update ->
                briefState.value = briefState.value.copy(brief = update.brief, source = update.source, briefIsFinal = update.isFinal)
                if (update.isFinal) flashDone()
            }
        }
    }

    fun onListeningChanged(active: Boolean) {
        listening.value = active
        if (active) transcript.value = null
    }

    /** Final words are held for a moment so the user can catch a misheard phrase before it's sent. */
    fun onTranscript(t: Transcript) {
        sendJob?.cancel()
        if (!t.isFinal) {
            transcript.value = TranscriptUi.Live(t.text)
            return
        }
        transcript.value = TranscriptUi.Confirming(t.text)
        sendJob = viewModelScope.launch {
            delay(CONFIRM_MS)
            transcript.value = null
            commands.submit(t.text)
        }
    }

    /** Stops the pending auto-send and hands the words back for editing. */
    fun editTranscript(): String? {
        sendJob?.cancel()
        val text = (transcript.value as? TranscriptUi.Confirming)?.text ?: (transcript.value as? TranscriptUi.Live)?.text
        transcript.value = null
        return text
    }

    fun submitText(text: String) {
        if (text.isNotBlank()) commands.submit(text.trim())
    }

    fun applyPending() = commands.applyPending()
    fun dismissPending() = commands.dismissPending()
    fun undo(applied: AppliedDiff) = commands.undo(applied)

    fun onApplied() = celebrate()

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleDone(task)
            if (task.status != TaskStatus.DONE) celebrate()
        }
    }

    fun setMuted(muted: Boolean) {
        viewModelScope.launch { prefs.setSpeechMuted(muted) }
    }

    private fun celebrate() {
        _burst.value += 1
        flashDone()
    }

    private fun flashDone() {
        viewModelScope.launch {
            doneFlash.value = true
            delay(DONE_FLASH_MS)
            doneFlash.value = false
        }
    }

    private companion object {
        const val CONFIRM_MS = 1_500L
        const val DONE_FLASH_MS = 900L
        const val UPCOMING_COUNT = 3
    }
}
