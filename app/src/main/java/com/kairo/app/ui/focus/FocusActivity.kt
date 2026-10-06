package com.kairo.app.ui.focus

import android.Manifest
import com.kairo.app.ui.design.laneStyle
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.ai.speech.AndroidSpeechProvider
import com.kairo.app.ai.speech.SpeechError
import com.kairo.app.ai.speech.SpeechProvider
import com.kairo.app.data.local.FocusOutcome
import com.kairo.app.data.local.FocusSession
import com.kairo.app.domain.focus.ClockReading
import com.kairo.app.domain.focus.FocusTiming
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.util.parseHexColor

/**
 * The running session (remaining time, Extend, Done, Drop) and the "next step" capture after it
 * ends. Only ever opened by the user (chip, notification tap), never launched from the background.
 * The mic is created here and only started from the mic button while this screen is visible (rule 6).
 */
class FocusActivity : ComponentActivity() {
    private lateinit var speech: AndroidSpeechProvider
    private var requestedMode by mutableStateOf(FocusMode.SESSION)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        speech = AndroidSpeechProvider(this)
        requestedMode = modeOf(intent)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return finish()
        setContent {
            KairoTheme {
                val viewModel: FocusViewModel = viewModel(
                    factory = containerFactory { FocusViewModel(sessionId, it.focusRepository, it.focusEngine, it.roleRepository) },
                )
                FocusRoute(viewModel, speech, requestedMode, onModeChange = { requestedMode = it }, onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A different session's notification opens a fresh screen rather than reusing this one's ViewModel.
        if (intent.getLongExtra(EXTRA_SESSION_ID, -1) != getIntent().getLongExtra(EXTRA_SESSION_ID, -1)) {
            finish()
            startActivity(intent)
            return
        }
        setIntent(intent)
        requestedMode = modeOf(intent)
    }

    override fun onPause() {
        super.onPause()
        speech.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.release()
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_MODE = "mode"

        private fun modeOf(intent: Intent) =
            intent.getStringExtra(EXTRA_MODE)?.let { runCatching { FocusMode.valueOf(it) }.getOrNull() } ?: FocusMode.SESSION

        fun sessionIntent(context: Context, sessionId: Long): Intent = Intent(context, FocusActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_MODE, FocusMode.SESSION.name)

        fun nextStepIntent(context: Context, sessionId: Long): Intent = Intent(context, FocusActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_MODE, FocusMode.NEXT_STEP.name)
    }
}

@Composable
private fun FocusRoute(
    viewModel: FocusViewModel,
    speech: SpeechProvider,
    requestedMode: FocusMode,
    onModeChange: (FocusMode) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val session = state.session
    val mode = session?.let { viewModel.modeFor(requestedMode, it) }
    if (session == null || mode == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val roleColor = state.role?.let { laneStyle(it).color } ?: KairoTheme.colors.primary

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        when (mode) {
            FocusMode.SESSION -> {
                val remaining = rememberFocusRemaining(session)
                val progress = progressOf(session, remaining)
                FocusSessionContent(
                    title = session.blockLabel,
                    roleColor = roleColor,
                    remainingMillis = remaining,
                    progress = progress,
                    extendedMinutes = session.extendedMinutes,
                    onExtend = viewModel::extend,
                    onDone = { viewModel.done { onModeChange(FocusMode.NEXT_STEP) } },
                    onDrop = { viewModel.drop(onClose) },
                    onClose = onClose,
                )
            }
            FocusMode.NEXT_STEP -> NextStepRoute(viewModel, speech, session, onClose)
        }
    }
}

/** Progress from remaining time, so the ring and the countdown can never disagree. */
private fun progressOf(session: FocusSession, remainingMillis: Long): Float {
    val total = FocusTiming.plannedDurationMillis(session).coerceAtLeast(1)
    return (1f - remainingMillis.toFloat() / total).coerceIn(0f, 1f)
}

@Composable
private fun NextStepRoute(viewModel: FocusViewModel, speech: SpeechProvider, session: FocusSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val draft by viewModel.nextStepDraft.collectAsStateWithLifecycle()
    val listening by speech.listening.collectAsStateWithLifecycle()
    var micRefused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadDraft() }
    LaunchedEffect(speech) { speech.results.collect { viewModel.onDraftChange(it.text) } }
    LaunchedEffect(speech) { speech.errors.collect { if (it == SpeechError.NO_PERMISSION) micRefused = true } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else micRefused = true
    }
    NextStepContent(
        title = session.blockLabel,
        text = draft.orEmpty(),
        onTextChange = viewModel::onDraftChange,
        micAvailable = speech.isAvailable && !micRefused,
        listening = listening,
        onMic = {
            when {
                listening -> speech.stop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> speech.start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onSave = { viewModel.saveNextStep(onClose) },
        onSkip = { viewModel.skipNextStep(onClose) },
        wasDropped = session.outcome == FocusOutcome.DROPPED,
    )
}

@Composable
fun FocusSessionContent(
    title: String,
    roleColor: Color,
    remainingMillis: Long,
    progress: Float,
    extendedMinutes: Int,
    onExtend: () -> Unit,
    onDone: () -> Unit,
    onDrop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(240.dp),
                color = roleColor,
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatCountdown(remainingMillis), style = KairoTheme.numbers.large)
                Text(
                    if (remainingMillis > 0) stringResource(R.string.focus_left) else stringResource(R.string.focus_time_up),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (extendedMinutes > 0) {
            Text(
                stringResource(R.string.focus_extended_by, extendedMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDrop, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.focus_action_drop)) }
            FilledTonalButton(onClick = onExtend, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.focus_action_extend)) }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.focus_action_done)) }
        }
    }
}

@Composable
fun NextStepContent(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    micAvailable: Boolean,
    listening: Boolean,
    onMic: () -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    wasDropped: Boolean = false,
) {
    Column(
        modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (wasDropped) R.string.focus_next_step_title_dropped else R.string.focus_next_step_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.focus_next_step_why), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.focus_next_step_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // A stray Done key mustn't wipe an existing next step with a blank one.
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave() }),
                modifier = Modifier.weight(1f),
            )
            if (micAvailable) {
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onMic) {
                    Icon(
                        if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = stringResource(if (listening) R.string.brief_stop_listening else R.string.brief_mic),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.focus_skip)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/** Sample session for @Preview only. */
internal object PreviewFocus {
    private val now = ClockReading(wallMillis = 1_791_000_000_000, elapsedMillis = 5_000_000, bootCount = 3)
    val session = FocusTiming.newSession(taskId = 2, label = "Edit reel #12", roleId = 4, minutes = 25, now = now)
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun FocusSessionContentPreview() {
    KairoTheme {
        FocusSessionContent(
            title = "Edit reel #12",
            roleColor = Color(0xFFFF2E93),
            remainingMillis = 17 * 60_000L + 32_000,
            progress = 0.3f,
            extendedMinutes = 10,
            onExtend = {},
            onDone = {},
            onDrop = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun NextStepContentPreview() {
    KairoTheme {
        NextStepContent(
            title = "Edit reel #12",
            text = "Add captions to the second half",
            onTextChange = {},
            micAvailable = true,
            listening = false,
            onMic = {},
            onSave = {},
            onSkip = {},
        )
    }
}
