package com.kairo.app.ui.briefing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.R
import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoSnackbarHost
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.Tone
import com.kairo.app.ui.design.components.glass
import com.kairo.app.ui.design.laneStyle
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

/**
 * Full-screen, dark, the orb as the hero. Below it: the greeting typed out, the summary, the best
 * gap, the if-then plan and the next three items. The input row stays at the bottom.
 */
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
    val colors = KairoTheme.colors
    Box(
        modifier
            .fillMaxSize()
            // Translucent window: a deep scrim keeps text readable over whatever is behind it.
            .background(colors.background.copy(alpha = SCRIM_ALPHA))
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopRow(muted, onToggleMute, onClose)
            topNotice()
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Orb(orbState, micLevel, running, Modifier.size(220.dp))
                    ParticleBurst(burst, running, Modifier.size(320.dp))
                }
                OrbStateLabel(orbState)
                val brief = state.brief
                if (brief == null) {
                    Text(stringResource(R.string.brief_loading), style = KairoTheme.type.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
                } else {
                    BriefText(brief, state.source, running)
                    brief.bestGap?.let { BestGapCard(it) }
                    brief.ifThenPlans.firstOrNull()?.let { IfThenCard(it) }
                    UpNext(state.upcoming.take(UP_NEXT_COUNT), onTaskClick)
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            state.transcript?.let { TranscriptBubble(it, input.onEditTranscript) }
            InputRow(input)
        }
        KairoSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

private const val SCRIM_ALPHA = 0.96f
private const val UP_NEXT_COUNT = 3

@Composable
private fun TopRow(muted: Boolean, onToggleMute: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
        KairoIconButton(
            if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            stringResource(if (muted) R.string.brief_unmute else R.string.brief_mute),
            onToggleMute,
        )
        Spacer(Modifier.weight(1f))
        KairoIconButton(Icons.Outlined.Close, stringResource(R.string.brief_close), onClose)
    }
}

/** Every orb state also says what it's doing, so the state never depends on motion or colour alone. */
@Composable
private fun OrbStateLabel(state: OrbState) {
    val (text, tone) = when (state) {
        OrbState.IDLE -> R.string.orb_idle to Tone.NEUTRAL
        OrbState.LISTENING -> R.string.orb_listening to Tone.PRIMARY
        OrbState.THINKING -> R.string.orb_thinking to Tone.PRIMARY
        OrbState.SPEAKING -> R.string.orb_speaking to Tone.PRIMARY
        OrbState.DONE -> R.string.orb_done to Tone.SUCCESS
    }
    val label = stringResource(text)
    StatusPill(label, tone = tone, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun BriefText(brief: Brief, source: BriefSource?, running: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TypewriterText(brief.greeting, running = running, style = KairoTheme.type.headlineMedium)
        Text(
            brief.summary,
            style = KairoTheme.type.bodyLarge,
            textAlign = TextAlign.Center,
            color = KairoTheme.colors.textSecondary,
            modifier = Modifier.animateContentSize(),
        )
        if (source == BriefSource.LOCAL) StatusPill(stringResource(R.string.brief_offline), icon = Icons.Outlined.CloudOff)
    }
}

/** Reveals text a few characters per frame; shows it whole when animation is paused or turned off. */
@Composable
private fun TypewriterText(text: String, running: Boolean, style: TextStyle) {
    val animate = running && !KairoTheme.reducedMotion
    var shown by remember(text) { mutableIntStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(text, animate) {
        if (!animate) {
            shown = text.length
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (shown < text.length) {
            withFrameNanos { now -> shown = (((now - start) / NANOS_PER_CHAR).toInt()).coerceAtMost(text.length) }
        }
    }
    // TalkBack reads the whole greeting at once, not letter by letter.
    Text(text.take(shown), style = style, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = text })
}

private const val NANOS_PER_CHAR = 28_000_000L

@Composable
private fun BestGapCard(gap: BestGap) {
    val context = LocalContext.current
    val colors = KairoTheme.colors
    val range = stringResource(R.string.time_range, formatMinuteOfDay(context, gap.startMinute), formatMinuteOfDay(context, gap.endMinute))
    GlassCard(level = GlassLevel.ONE, elevation = Elevation.RAISED, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.success)
            Spacer(Modifier.width(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.brief_best_gap_title, range), style = KairoTheme.type.titleSmall)
                Text(gap.suggestion, style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun IfThenCard(plan: String) {
    GlassCard(level = GlassLevel.ONE) {
        Text(stringResource(R.string.brief_if_then_label).uppercase(), style = KairoTheme.type.labelSmall, color = KairoTheme.colors.primary)
        Text(plan, style = KairoTheme.type.bodyMedium)
    }
}

@Composable
private fun UpNext(entries: List<TimelineEntry>, onTaskClick: (Task) -> Unit) {
    val context = LocalContext.current
    val colors = KairoTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.brief_up_next))
        if (entries.isEmpty()) {
            Text(stringResource(R.string.brief_nothing_next), style = KairoTheme.type.bodyMedium, color = colors.textSecondary)
        }
        entries.forEach { entry ->
            val task = (entry as? TimelineEntry.TaskEntry)?.task
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.medium)
                    .then(if (task != null) Modifier.clickable { onTaskClick(task) } else Modifier)
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(Radius.full).background(laneStyle(entry.role).color))
                Spacer(Modifier.width(Spacing.md))
                Text(
                    entry.startMinute?.let { formatMinuteOfDay(context, it) }.orEmpty(),
                    style = KairoTheme.numbers.small,
                    color = colors.textSecondary,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = KairoTheme.type.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(entry.role?.name, task?.nextStep?.let { stringResource(R.string.timeline_next_step, it) })
                    if (sub.isNotEmpty()) Text(sub.joinToString(stringResource(R.string.list_separator)), style = KairoTheme.type.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(transcript: TranscriptUi, onEdit: () -> Unit) {
    val colors = KairoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .glass(colors.surface3, Radius.medium, Elevation.RAISED)
            .clickable(onClick = onEdit)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when (transcript) {
            is TranscriptUi.Live -> Text(transcript.text, style = KairoTheme.type.bodyLarge, color = colors.textSecondary)
            is TranscriptUi.Confirming -> {
                Text(transcript.text, style = KairoTheme.type.bodyLarge)
                Text(stringResource(R.string.brief_tap_to_edit), style = KairoTheme.type.labelSmall, color = colors.primary)
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(Radius.full), color = colors.primary, trackColor = colors.surface2)
            }
        }
    }
}

@Composable
private fun InputRow(input: BriefingInput) {
    val colors = KairoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mic only appears when permission isn't refused and the device has a recogniser (rule 6: in-activity only).
        if (input.micAvailable) {
            KairoIconButton(
                if (input.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                stringResource(if (input.listening) R.string.brief_stop_listening else R.string.brief_mic),
                input.onMic,
                filled = true,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Row(
            Modifier.weight(1f).glass(colors.surface2, Radius.full, Elevation.RAISED).padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hint = stringResource(R.string.brief_type_hint)
            Box(Modifier.weight(1f).padding(vertical = Spacing.md)) {
                if (input.text.isEmpty()) Text(hint, style = KairoTheme.type.bodyLarge, color = colors.textTertiary, maxLines = 1)
                BasicTextField(
                    value = input.text,
                    onValueChange = input.onTextChange,
                    singleLine = true,
                    textStyle = KairoTheme.type.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { input.onSend() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                )
            }
            KairoIconButton(
                Icons.AutoMirrored.Outlined.Send,
                stringResource(R.string.command_send),
                input.onSend,
                tint = if (input.text.isNotBlank()) colors.primary else colors.textTertiary,
                enabled = input.text.isNotBlank(),
            )
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 860)
@Composable
private fun BriefingContentPreview() {
    PreviewBriefing(
        BriefingUiState(brief = previewBrief, source = BriefSource.CLOUD, briefIsFinal = true, upcoming = PreviewData.todayState.entries.take(3)),
        OrbState.IDLE,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 860)
@Composable
private fun BriefingListeningPreview() {
    PreviewBriefing(
        BriefingUiState(brief = previewBrief.copy(bestGap = null), source = BriefSource.LOCAL, briefIsFinal = true, transcript = TranscriptUi.Confirming("gym skip kar")),
        OrbState.LISTENING,
        previewInput.copy(listening = true),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 860)
@Composable
private fun BriefingLoadingNoMicPreview() {
    PreviewBriefing(BriefingUiState(), OrbState.THINKING, previewInput.copy(micAvailable = false))
}

/** Shared with screenshot tests. */
object BriefingPreviewData {
    val brief get() = previewBrief
}

