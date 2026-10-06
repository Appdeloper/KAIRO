package com.kairo.app.ui.briefing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.kairo.app.ai.speech.AndroidSpeechProvider
import com.kairo.app.ai.speech.BriefSpeaker
import com.kairo.app.ai.speech.SpeechError
import com.kairo.app.ai.speech.SpeechProvider
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.command.CommandFeedbackEffect
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.today.DayTimelineSource
import com.kairo.app.ui.today.PlanDiffSheet
import com.kairo.app.R
import com.kairo.app.ui.shake.ShakeStoppedCard
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

/**
 * The orb briefing. Opened from the app, the Quick Settings tile, the home-screen widget and the
 * "Brief me" shortcut. The mic is only ever started here, while this activity is visible.
 */
class BriefingActivity : ComponentActivity() {

    private val viewModel: BriefingViewModel by viewModels {
        containerFactory {
            BriefingViewModel(
                briefing = it.briefingRepository,
                strings = ResourceBriefStrings(applicationContext),
                taskRepository = it.taskRepository,
                timeline = DayTimelineSource(it.taskRepository, it.timetableRepository, it.roleRepository, it.dateProvider),
                prefs = it.userPrefsRepository,
                dates = it.dateProvider,
                parser = it.commandParser,
                executor = it.commandExecutor,
                planRepository = it.planRepository,
            )
        }
    }

    private var listenOnOpen = false
    private lateinit var speech: AndroidSpeechProvider
    private lateinit var speaker: BriefSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        stopAlarmIfAsked(intent)
        // Only a fresh open from the orb button listens straight away; a rotation must not re-trigger it.
        listenOnOpen = savedInstanceState == null && intent.getBooleanExtra(EXTRA_LISTEN, false)
        speech = AndroidSpeechProvider(this)
        speaker = BriefSpeaker(this)
        setContent {
            KairoTheme { BriefingRoute(viewModel, speech, speaker, listenOnOpen = listenOnOpen, onClose = ::finish) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stopAlarmIfAsked(intent)
        // Re-opened from a tile/widget/shortcut while still alive: the plan may have changed.
        viewModel.refreshBrief()
    }

    override fun onPause() {
        // Never keep listening or talking once the user can't see us.
        speech.cancel()
        speaker.stop()
        super.onPause()
    }

    override fun onDestroy() {
        speech.release()
        speaker.shutdown()
        super.onDestroy()
    }

    /** "Dismiss" on the alarm notification opens us directly; we then stop the ringing. */
    private fun stopAlarmIfAsked(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_DISMISS_ALARM_ID) == true) {
            startService(com.kairo.app.alarm.AlarmRingService.dismissIntent(this))
            intent.removeExtra(EXTRA_DISMISS_ALARM_ID)
        }
    }

    companion object {
        private const val EXTRA_DISMISS_ALARM_ID = "dismiss_alarm_id"
        const val EXTRA_LISTEN = "listen_on_open"

        fun intent(context: Context): Intent = Intent(context, BriefingActivity::class.java)

        /** From the floating orb: the user tapped to talk, so the mic opens as soon as the screen is up. */
        fun voiceIntent(context: Context): Intent = intent(context).putExtra(EXTRA_LISTEN, true)

        fun dismissAlarmIntent(context: Context, alarmId: Long): Intent = intent(context).putExtra(EXTRA_DISMISS_ALARM_ID, alarmId)
    }
}

@Composable
private fun BriefingRoute(viewModel: BriefingViewModel, speech: SpeechProvider, speaker: BriefSpeaker, listenOnOpen: Boolean, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val burst by viewModel.burst.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val pendingDiff by viewModel.pendingDiff.collectAsStateWithLifecycle()
    val level by speech.level.collectAsStateWithLifecycle()
    val listening by speech.listening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var text by rememberSaveable { mutableStateOf("") }
    var micRefused by rememberSaveable { mutableStateOf(false) }
    val noSpeechMessage = androidx.compose.ui.res.stringResource(R.string.brief_no_speech)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else micRefused = true
    }
    fun onMic() {
        when {
            listening -> speech.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> speech.start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(listening) { viewModel.onListeningChanged(listening) }
    LaunchedEffect(speech) { speech.results.collect(viewModel::onTranscript) }
    LaunchedEffect(speech) {
        speech.errors.collect { error ->
            if (error == SpeechError.NO_PERMISSION) micRefused = true else snackbar.showSnackbar(noSpeechMessage)
        }
    }
    CommandFeedbackEffect(viewModel.events, snackbar, onUndo = viewModel::undo, onApplied = viewModel::onApplied)
    // Opened to talk: start listening once the screen is resumed, and don't read the brief aloud over
    // the mic. Starting here is still "inside a visible activity" (rule 6).
    var listenRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (listenOnOpen && resumed && !listenRequested && speech.isAvailable) {
            listenRequested = true
            onMic()
        }
    }
    SpeakBriefEffect(state, resumed, muted || listenOnOpen, speaker)

    BriefingContent(
        topNotice = { ShakeStoppedCard(Modifier.padding(horizontal = 16.dp)) },
        state = state,
        orbState = orbState,
        micLevel = level,
        running = resumed,
        burst = burst,
        muted = muted,
        input = BriefingInput(
            text = text,
            onTextChange = { text = it },
            onSend = {
                viewModel.submitText(text)
                text = ""
            },
            micAvailable = speech.isAvailable && !micRefused,
            listening = listening,
            onMic = ::onMic,
            onEditTranscript = { viewModel.editTranscript()?.let { text = it } },
        ),
        snackbar = snackbar,
        onToggleMute = { viewModel.setMuted(!muted) },
        onClose = onClose,
        onTaskClick = viewModel::toggleDone,
    )

    pendingDiff?.let { diff ->
        PlanDiffSheet(diff = diff, today = state.today, onApply = viewModel::applyPending, onCancel = viewModel::dismissPending)
    }
}

/**
 * Speaks greeting + summary once per brief, only while resumed and unmuted. If speech is cut off
 * by a pause, the brief is spoken again on resume because it never reported done.
 */
@Composable
private fun SpeakBriefEffect(state: BriefingUiState, resumed: Boolean, muted: Boolean, speaker: BriefSpeaker) {
    var spoken by rememberSaveable { mutableStateOf<String?>(null) }
    val brief = state.brief
    val utterance = brief?.let { "${it.greeting}. ${it.summary}" }
    LaunchedEffect(utterance, state.briefIsFinal, resumed, muted) {
        when {
            !resumed || muted -> speaker.stop()
            utterance != null && state.briefIsFinal && spoken != utterance -> speaker.speak(utterance) { spoken = utterance }
        }
    }
}
