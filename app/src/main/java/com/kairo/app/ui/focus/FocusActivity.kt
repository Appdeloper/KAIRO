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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.rememberKairoHaptics
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.KairoTextField
import com.kairo.app.ui.design.components.OrbGlyph
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.ProgressRing
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.Tone
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

    Box(Modifier.fillMaxSize().background(KairoTheme.colors.background).safeDrawingPadding()) {
        when (mode) {
            FocusMode.SESSION -> {
                val remaining = rememberFocusRemaining(session)
                val progress = progressOf(session, remaining)
                FocusSessionContent(
                    title = session.blockLabel,
                    roleColor = roleColor,
                    laneName = state.role?.name,
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
    laneName: String? = null,
) {
    val colors = KairoTheme.colors
    val haptics = rememberKairoHaptics()
    val countdown = formatCountdown(remainingMillis)
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(stringResource(R.string.focus_mode_label), tone = Tone.PRIMARY, icon = Icons.Outlined.Timer)
            Spacer(Modifier.weight(1f))
            KairoTextButton(stringResource(R.string.action_close), onClose, color = colors.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = KairoTheme.type.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (laneName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(Radius.full).background(roleColor))
                Spacer(Modifier.width(Spacing.sm))
                Text(laneName, style = KairoTheme.type.labelLarge, color = colors.textSecondary)
            }
        }
        val ringLabel = if (remainingMillis > 0) stringResource(R.string.focus_ring_cd, countdown) else stringResource(R.string.focus_time_up)
        ProgressRing(progress, size = 260.dp, strokeWidth = 12.dp, color = roleColor, modifier = Modifier.semantics { contentDescription = ringLabel }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(countdown, style = KairoTheme.numbers.large.copy(fontSize = 56.sp, lineHeight = 60.sp))
                Text(
                    if (remainingMillis > 0) stringResource(R.string.focus_left) else stringResource(R.string.focus_time_up),
                    style = KairoTheme.type.labelLarge,
                    color = colors.textSecondary,
                )
            }
        }
        if (extendedMinutes > 0) {
            StatusPill(stringResource(R.string.focus_extended_by, extendedMinutes), tone = Tone.NEUTRAL)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            stringResource(R.string.focus_action_done),
            onClick = {
                haptics.success()
                onDone()
            },
            icon = Icons.Outlined.Check,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SecondaryButton(stringResource(R.string.focus_action_drop), onDrop, Modifier.weight(1f), tint = colors.textSecondary)
            SecondaryButton(
                stringResource(R.string.focus_action_extend),
                onClick = {
                    haptics.tick()
                    onExtend()
                },
                modifier = Modifier.weight(1f),
            )
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
    val colors = KairoTheme.colors
    Column(
        modifier.fillMaxSize().imePadding().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        OrbGlyph(size = 64.dp)
        Text(
            stringResource(if (wasDropped) R.string.focus_next_step_title_dropped else R.string.focus_next_step_title),
            style = KairoTheme.type.headlineLarge,
        )
        Text(title, style = KairoTheme.type.titleMedium, color = colors.textSecondary)
        Text(stringResource(R.string.focus_next_step_why), style = KairoTheme.type.bodyLarge, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KairoTextField(
                value = text,
                onValueChange = onTextChange,
                label = stringResource(R.string.focus_next_step_label),
                placeholder = stringResource(R.string.focus_next_step_hint),
                // A stray Done key mustn't wipe an existing next step with a blank one.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave() }),
                modifier = Modifier.weight(1f),
            )
            if (micAvailable) {
                Spacer(Modifier.width(Spacing.sm))
                KairoIconButton(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    stringResource(if (listening) R.string.brief_stop_listening else R.string.brief_mic),
                    onMic,
                    filled = true,
                )
            }
        }
        if (listening) StatusPill(stringResource(R.string.orb_listening), tone = Tone.PRIMARY)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.focus_save_next_step), onSave, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
        KairoTextButton(stringResource(R.string.focus_skip), onSkip, Modifier.fillMaxWidth(), color = colors.textSecondary)
    }
}

/** Sample session for @Preview only. */
internal object PreviewFocus {
    private val now = ClockReading(wallMillis = 1_791_000_000_000, elapsedMillis = 5_000_000, bootCount = 3)
    val session = FocusTiming.newSession(taskId = 2, label = "Edit reel #12", roleId = 4, minutes = 25, now = now)
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 851)
@Composable
private fun FocusSessionContentPreview() {
    KairoTheme {
        FocusSessionContent(
            title = "Edit reel #12",
            roleColor = Color(0xFFFF6FB7),
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 851)
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

/** Sample session for previews and screenshot tests outside this package. */
object PreviewFocusAccess {
    val session get() = PreviewFocus.session
}
