package com.kairo.app.ui.briefing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.components.RoleDot
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.util.formatMinuteOfDay

/** Everything the input row needs; grouped so the content composable stays readable. */
data class BriefingInput(
    val text: String,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val micAvailable: Boolean,
    val listening: Boolean,
    val onMic: () -> Unit,
    val onEditTranscript: () -> Unit,
)

@Composable
fun BriefingContent(
    state: BriefingUiState,
    orbState: OrbState,
    micLevel: Float,
    running: Boolean,
    burst: Int,
    muted: Boolean,
    input: BriefingInput,
    snackbar: SnackbarHostState,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
    topNotice: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxSize()
            // Translucent window: a deep scrim keeps text readable over whatever is behind it.
            .background(KairoTheme.colors.background.copy(alpha = SCRIM_ALPHA))
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopRow(muted, onToggleMute, onClose)
            topNotice()
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Orb(orbState, micLevel, running, Modifier.size(200.dp))
                    ParticleBurst(burst, running, Modifier.size(320.dp))
                }
                state.brief?.let { BriefText(it, state.source, running) }
                state.brief?.bestGap?.let { BestGapChip(it) }
                state.brief?.ifThenPlans?.firstOrNull()?.let { IfThenCard(it) }
                UpNext(state.upcoming, onTaskClick)
                Spacer(Modifier.height(8.dp))
            }
            state.transcript?.let { TranscriptBubble(it, input.onEditTranscript) }
            InputRow(input)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

private const val SCRIM_ALPHA = 0.94f

@Composable
private fun TopRow(muted: Boolean, onToggleMute: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        IconButton(onClick = onToggleMute) {
            Icon(
                if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = stringResource(if (muted) R.string.brief_unmute else R.string.brief_mute),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.brief_close)) }
    }
}

@Composable
private fun BriefText(brief: Brief, source: BriefSource?, running: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TypewriterText(
            brief.greeting,
            running = running,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            brief.summary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.animateContentSize(),
        )
        if (source == BriefSource.LOCAL) {
            Text(stringResource(R.string.brief_offline), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Reveals text a few characters per frame; shows it whole when animation is paused. */
@Composable
private fun TypewriterText(text: String, running: Boolean, style: androidx.compose.ui.text.TextStyle, fontWeight: FontWeight, color: Color) {
    var shown by remember(text) { mutableIntStateOf(if (running) 0 else text.length) }
    LaunchedEffect(text, running) {
        if (!running) {
            shown = text.length
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (shown < text.length) {
            withFrameNanos { now -> shown = (((now - start) / NANOS_PER_CHAR).toInt()).coerceAtMost(text.length) }
        }
    }
    Text(text.take(shown), style = style, fontWeight = fontWeight, color = color, textAlign = TextAlign.Center)
}

private const val NANOS_PER_CHAR = 28_000_000L

@Composable
private fun BestGapChip(gap: BestGap) {
    val context = LocalContext.current
    val range = stringResource(R.string.time_range, formatMinuteOfDay(context, gap.startMinute), formatMinuteOfDay(context, gap.endMinute))
    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.brief_best_gap, range, gap.suggestion)) },
        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = KairoTheme.colors.success) },
        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun IfThenCard(plan: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(plan, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun UpNext(entries: List<TimelineEntry>, onTaskClick: (Task) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.brief_up_next), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (entries.isEmpty()) {
            Text(stringResource(R.string.brief_nothing_next), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { entry ->
            val task = (entry as? TimelineEntry.TaskEntry)?.task
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (task != null) Modifier.clickable { onTaskClick(task) } else Modifier)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoleDot(entry.role)
                Spacer(Modifier.width(12.dp))
                Text(
                    entry.startMinute?.let { formatMinuteOfDay(context, it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp),
                )
                Column {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                    task?.nextStep?.let { step ->
                        Text(
                            stringResource(R.string.timeline_next_step, step),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(transcript: TranscriptUi, onEdit: () -> Unit) {
    AnimatedVisibility(visible = true) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onEdit),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (transcript) {
                    is TranscriptUi.Live -> Text(transcript.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is TranscriptUi.Confirming -> {
                        Text(transcript.text, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.brief_tap_to_edit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun InputRow(input: BriefingInput) {
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mic only appears when permission isn't refused and the device has a recogniser (rule 6: in-activity only).
        if (input.micAvailable) {
            FilledIconButton(onClick = input.onMic) {
                Icon(
                    if (input.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = stringResource(if (input.listening) R.string.brief_stop_listening else R.string.brief_mic),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        OutlinedTextField(
            value = input.text,
            onValueChange = input.onTextChange,
            placeholder = { Text(stringResource(R.string.brief_type_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { input.onSend() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = input.onSend, enabled = input.text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.command_send))
        }
    }
}

// ---- previews ----

private val previewInput = BriefingInput("", {}, {}, micAvailable = true, listening = false, onMic = {}, onEditTranscript = {})

private val previewBrief = Brief(
    greeting = "Good morning, Aarav",
    summary = "3 things left today. Next up: DBMS lecture at 9:00 AM, in 25 min.",
    bestGap = BestGap(13 * 60 + 10, 15 * 60 - 10, "1 h 40 min free: good for deep work."),
    ifThenPlans = listOf("If DBMS lecture runs over, then shift the next task, not your break."),
)

@Composable
private fun PreviewBriefing(state: BriefingUiState, orbState: OrbState, input: BriefingInput = previewInput) {
    KairoTheme {
        BriefingContent(
            state = state, orbState = orbState, micLevel = 0.5f, running = false, burst = 0, muted = false,
            input = input, snackbar = SnackbarHostState(), onToggleMute = {}, onClose = {}, onTaskClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 800)
@Composable
private fun BriefingContentPreview() {
    PreviewBriefing(
        BriefingUiState(brief = previewBrief, source = BriefSource.CLOUD, briefIsFinal = true, upcoming = PreviewData.todayState.entries.take(3)),
        OrbState.IDLE,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 800)
@Composable
private fun BriefingListeningPreview() {
    PreviewBriefing(
        BriefingUiState(brief = previewBrief.copy(bestGap = null), source = BriefSource.LOCAL, briefIsFinal = true, transcript = TranscriptUi.Confirming("gym skip kar")),
        OrbState.LISTENING,
        previewInput.copy(listening = true),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, heightDp = 800)
@Composable
private fun BriefingLoadingNoMicPreview() {
    PreviewBriefing(BriefingUiState(), OrbState.THINKING, previewInput.copy(micAvailable = false))
}
