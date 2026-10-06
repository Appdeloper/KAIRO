package com.kairo.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.R
import com.kairo.app.data.local.FocusSession
import com.kairo.app.data.local.Task
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.alarms.AlarmPermissionBanner
import com.kairo.app.ui.alarms.formatInstant
import com.kairo.app.ui.alarms.rememberAlarmHealth
import com.kairo.app.ui.alarms.rememberHealthFixer
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.ui.command.CommandFeedbackEffect
import com.kairo.app.ui.containerFactory
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.MinTouchTarget
import com.kairo.app.ui.design.Motion
import com.kairo.app.ui.design.Radius
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.EmptyState
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoScaffold
import com.kairo.app.ui.design.components.KairoSnackbarHost
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.NowMarker
import com.kairo.app.ui.design.components.ProgressRing
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.TimelineItem
import com.kairo.app.ui.design.components.TimelineState
import com.kairo.app.ui.design.components.Tone
import com.kairo.app.ui.design.components.glass
import com.kairo.app.ui.design.laneStyle
import com.kairo.app.ui.design.rememberKairoHaptics
import com.kairo.app.ui.focus.FocusActivity
import com.kairo.app.ui.focus.FocusLauncherViewModel
import com.kairo.app.ui.focus.FocusStartSheet
import com.kairo.app.ui.focus.FocusTarget
import com.kairo.app.ui.focus.focusLauncherFactory
import com.kairo.app.ui.focus.formatCountdown
import com.kairo.app.ui.focus.rememberFocusRemaining
import com.kairo.app.ui.plan.PlanSegment
import com.kairo.app.ui.shake.ShakeStoppedCard
import com.kairo.app.util.formatMinuteOfDay
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A running focus session as Today shows it. */
data class FocusUi(val session: FocusSession, val remainingMillis: Long)

@Composable
fun TodayScreen(
    onOpenAlarms: () -> Unit = {},
    onOpenPlan: (PlanSegment) -> Unit = {},
    focus: FocusLauncherViewModel = viewModel(factory = focusLauncherFactory()),
    viewModel: TodayViewModel = viewModel(
        factory = containerFactory {
            TodayViewModel(
                it.taskRepository, it.timetableRepository, it.roleRepository, it.userPrefsRepository,
                it.dateProvider, it.planRepository, it.commandExecutor, it.commandParser, it.alarmRepository,
                loadSampleWeek = it::loadSampleWeek,
            )
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingDiff by viewModel.pendingDiff.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val alarmHealth = rememberAlarmHealth(alarms.next?.second)
    val fixAlarmIssue = rememberHealthFixer()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberKairoHaptics()
    CommandFeedbackEffect(viewModel.events, snackbarHostState, onUndo = viewModel::undo, onApplied = haptics::tick)
    val runningFocus by focus.running.collectAsStateWithLifecycle()
    val focusDefault by focus.defaultMinutes.collectAsStateWithLifecycle()
    var focusTarget by remember { mutableStateOf<FocusTarget?>(null) }
    LaunchedEffect(focus) { focus.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) } }
    LaunchedEffect(viewModel) {
        viewModel.sampleMessages.collect { loaded ->
            snackbarHostState.showSnackbar(resources.getString(if (loaded) R.string.sample_loaded else R.string.sample_already_loaded))
        }
    }
    val openFocus = { id: Long -> context.startActivity(FocusActivity.sessionIntent(context, id)) }
    val focusUi = runningFocus?.let { FocusUi(it, rememberFocusRemaining(it)) }

    KairoScaffold(
        snackbarHost = { KairoSnackbarHost(snackbarHostState) },
        bottomBar = {
            CommandBar(
                onSubmit = viewModel::submitCommand,
                onMic = { context.startActivity(BriefingActivity.voiceIntent(context)) },
            )
        },
    ) { padding ->
        TodayContent(
            state = state,
            onToggleDone = { task ->
                if (!(task.status == com.kairo.app.data.local.TaskStatus.DONE)) haptics.success()
                viewModel.toggleDone(task)
            },
            onEntryClick = { focusTarget = FocusTarget.from(it) },
            focus = focusUi,
            onOpenFocus = { focusUi?.let { openFocus(it.session.id) } },
            nextAlarm = alarms.next?.let { (plan, at) -> plan.label to at },
            onOpenAlarms = onOpenAlarms,
            onAddTimetable = { onOpenPlan(PlanSegment.TIMETABLE) },
            onLoadSample = viewModel::loadSample,
            notices = {
                ShakeStoppedCard()
                // Only nag about alarm permissions once the user actually relies on alarms.
                if (alarms.anyEnabled) AlarmPermissionBanner(alarmHealth, fixAlarmIssue)
            },
            modifier = Modifier.padding(padding),
        )
    }

    focusTarget?.let { target ->
        FocusStartSheet(
            target = target,
            defaultMinutes = focusDefault,
            running = runningFocus,
            onStart = { minutes ->
                focus.start(target, minutes)
                focusTarget = null
            },
            onOpenRunning = {
                runningFocus?.let { openFocus(it.id) }
                focusTarget = null
            },
            onDismiss = { focusTarget = null },
        )
    }

    pendingDiff?.let { diff ->
        PlanDiffSheet(diff = diff, today = state.date, onApply = viewModel::applyPending, onCancel = viewModel::dismissPending)
    }
}

/** Pinned under the timeline: type or say a change. Hinglish works; the hint rotates through examples. */
@Composable
fun CommandBar(onSubmit: (String) -> Unit, onMic: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val colors = KairoTheme.colors
    val hints = stringArrayResource(R.array.command_hints)
    var hintIndex by remember { mutableIntStateOf(0) }
    val reduced = KairoTheme.reducedMotion
    LaunchedEffect(text.isEmpty(), reduced) {
        while (text.isEmpty() && !reduced) {
            delay(HINT_ROTATE_MS)
            hintIndex = (hintIndex + 1) % hints.size
        }
    }
    val send = {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .imePadding()
            // The floating orb button overlaps the top of the bottom bar; this keeps it off the input.
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .glass(colors.surface2, Radius.full, Elevation.RAISED)
                .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hint = hints[hintIndex]
            Box(Modifier.weight(1f).padding(vertical = Spacing.md)) {
                if (text.isEmpty()) {
                    AnimatedContent(
                        targetState = hint,
                        transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.short()) },
                        label = "hint",
                    ) { Text(it, style = KairoTheme.type.bodyLarge, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = KairoTheme.type.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                )
            }
            if (text.isNotBlank()) {
                KairoIconButton(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.command_send), send, tint = colors.primary)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        KairoIconButton(Icons.Outlined.Mic, stringResource(R.string.command_mic), onMic, filled = true)
    }
}

private const val HINT_ROTATE_MS = 4_000L

@Composable
fun TodayContent(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onToggleDone: (Task) -> Unit = {},
    onEntryClick: (TimelineEntry) -> Unit = {},
    focus: FocusUi? = null,
    onOpenFocus: () -> Unit = {},
    nextAlarm: Pair<String, Instant>? = null,
    onOpenAlarms: () -> Unit = {},
    onAddTimetable: () -> Unit = {},
    onLoadSample: () -> Unit = {},
    notices: @Composable () -> Unit = {},
) {
    if (!state.loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingOrb(size = 72.dp) }
        return
    }
    val nowLine = NowSummary.nowLineIndex(state.entries, state.nowMinute)
    val summary = NowSummary.from(state.entries, state.nowMinute)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xl, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header") { Header(state.firstName, state.dayPart, state.date, nextAlarm, onOpenAlarms) }
        item(key = "notices") { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { notices() } }
        if (focus != null) item(key = "focus") { FocusRow(focus, onOpenFocus) }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.today_empty_title),
                    body = stringResource(R.string.today_empty_body),
                    actionLabel = stringResource(R.string.action_add_timetable),
                    onAction = onAddTimetable,
                    secondaryLabel = stringResource(R.string.action_load_sample),
                    onSecondary = onLoadSample,
                )
            }
            return@LazyColumn
        }
        item(key = "now") { NowCard(summary, state, focus) }
        item(key = "timelineHeader") { SectionHeader(stringResource(R.string.today_timeline)) }
        itemsIndexed(state.entries, key = { _, entry -> entry.key() }) { index, entry ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (index == nowLine) NowMarker(stringResource(R.string.today_now_marker, formatMinuteOfDay(LocalContext.current, state.nowMinute)))
                TimelineRow(entry, focus, onToggleDone, onEntryClick)
            }
        }
        if (nowLine >= state.entries.size) {
            item(key = "nowLineEnd") { NowMarker(stringResource(R.string.today_now_marker, formatMinuteOfDay(LocalContext.current, state.nowMinute))) }
        }
    }
}

private fun TimelineEntry.key(): String = when (this) {
    is TimelineEntry.Block -> "b${block.id}"
    is TimelineEntry.TaskEntry -> "t${task.id}"
}

@Composable
private fun Header(firstName: String, dayPart: DayPart, date: LocalDate, nextAlarm: Pair<String, Instant>?, onOpenAlarms: () -> Unit) {
    val greetingRes = when (dayPart) {
        DayPart.MORNING -> R.string.greeting_morning
        DayPart.AFTERNOON -> R.string.greeting_afternoon
        DayPart.EVENING -> R.string.greeting_evening
        DayPart.NIGHT -> R.string.greeting_night
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(greetingRes, firstName), style = KairoTheme.type.headlineMedium)
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = KairoTheme.type.bodyMedium, color = KairoTheme.colors.textSecondary)
        if (nextAlarm != null) {
            Spacer(Modifier.height(Spacing.xs))
            NextAlarmChip(nextAlarm.first, nextAlarm.second, onOpenAlarms)
        }
    }
}

/** What's on now, what's next, and how the day is going. */
@Composable
private fun NowCard(summary: NowSummary, state: TodayUiState, focus: FocusUi?) {
    val colors = KairoTheme.colors
    GlassCard(level = GlassLevel.TWO, elevation = Elevation.GLOW) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val current = summary.current
                if (current != null) {
                    StatusPill(stringResource(R.string.today_now), tone = Tone.PRIMARY)
                    Text(current.title, style = KairoTheme.type.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val endsIn = if (focus != null && focus.session.blockLabel == current.title) {
                        stringResource(R.string.today_focus_left, formatCountdown(focus.remainingMillis))
                    } else {
                        stringResource(R.string.today_ends_in, durationText(summary.minutesLeftInCurrent ?: 0))
                    }
                    Text(endsIn, style = KairoTheme.numbers.small, color = colors.textSecondary)
                } else {
                    StatusPill(stringResource(R.string.today_free_now), tone = Tone.NEUTRAL)
                    Text(
                        stringResource(if (summary.next == null) R.string.today_nothing_left else R.string.today_free_until_next),
                        style = KairoTheme.type.titleLarge,
                    )
                }
                summary.next?.let { next ->
                    Spacer(Modifier.height(Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(Radius.full).background(laneStyle(next.role).color))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            stringResource(R.string.today_next_line, next.title, durationText(summary.minutesUntilNext ?: 0)),
                            style = KairoTheme.type.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(Spacing.md))
            val done = state.progress.done
            val total = state.progress.total
            val progressDescription = stringResource(R.string.today_progress_cd, done, total)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = progressDescription }) {
                ProgressRing(state.progress.fraction, size = 76.dp) {
                    Text(stringResource(R.string.today_progress_count, done, total), style = KairoTheme.numbers.small)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.today_progress_label), style = KairoTheme.type.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FocusRow(focus: FocusUi, onClick: () -> Unit) {
    val colors = KairoTheme.colors
    GlassCard(level = GlassLevel.ONE, elevation = Elevation.RAISED, onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_focusing_on), style = KairoTheme.type.labelMedium, color = colors.textSecondary)
                Text(focus.session.blockLabel, style = KairoTheme.type.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatCountdown(focus.remainingMillis), style = KairoTheme.numbers.medium, color = colors.primary)
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, focus: FocusUi?, onToggleDone: (Task) -> Unit, onEntryClick: (TimelineEntry) -> Unit) {
    val context = LocalContext.current
    val colors = KairoTheme.colors
    val lane = laneStyle(entry.role)
    val taskEntry = entry as? TimelineEntry.TaskEntry
    val block = entry as? TimelineEntry.Block
    val focusing = focus != null && when (entry) {
        is TimelineEntry.TaskEntry -> focus.session.taskId == entry.task.id
        is TimelineEntry.Block -> focus.session.taskId == null && focus.session.blockLabel == entry.title
    }
    val state = when {
        focusing -> TimelineState.FOCUS
        taskEntry?.isDone == true -> TimelineState.DONE
        block?.skipped == true -> TimelineState.SKIPPED
        else -> TimelineState.UPCOMING
    }
    val stateText = when (state) {
        TimelineState.FOCUS -> stringResource(R.string.timeline_focusing, formatCountdown(focus!!.remainingMillis))
        TimelineState.DONE -> stringResource(R.string.timeline_done)
        TimelineState.SKIPPED -> stringResource(R.string.timeline_skipped)
        else -> null
    }
    val start = entry.startMinute
    TimelineItem(
        startTime = start?.let { formatMinuteOfDay(context, it) } ?: stringResource(R.string.today_anytime),
        endTime = entry.endMinute?.takeIf { start != null }?.let { formatMinuteOfDay(context, it) },
        title = entry.title,
        laneColor = lane.color,
        laneLabel = entry.role?.name,
        detail = block?.block?.location,
        nextStep = taskEntry?.task?.nextStep?.let { stringResource(R.string.timeline_next_step, it) },
        state = state,
        stateText = stateText,
        locked = block != null,
        lockedDescription = stringResource(R.string.cd_fixed_block),
        onClick = { onEntryClick(entry) },
        onClickLabel = stringResource(R.string.focus_start_title),
        trailing = taskEntry?.let { t ->
            {
                val done = t.isDone
                KairoIconButton(
                    if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    stringResource(if (done) R.string.cd_mark_not_done else R.string.cd_mark_done),
                    { onToggleDone(t.task) },
                    tint = if (done) colors.success else lane.color,
                )
            }
        },
    )
}

@Composable
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_minutes, m)
        m == 0 -> stringResource(R.string.duration_hours, h)
        else -> stringResource(R.string.duration_hours_minutes, h, m)
    }
}

@Composable
fun NextAlarmChip(label: String, at: Instant, onClick: () -> Unit) {
    val colors = KairoTheme.colors
    Row(
        Modifier
            .clip(Radius.full)
            .background(colors.surface2)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            stringResource(R.string.today_next_alarm, formatInstant(at), label.ifBlank { stringResource(R.string.alarm_default_label) }),
            style = KairoTheme.type.labelLarge,
            color = colors.textPrimary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F, heightDp = 900)
@Composable
private fun TodayContentPreview() {
    KairoTheme {
        TodayContent(
            state = PreviewData.todayState.copy(nowMinute = 10 * 60 + 42, loaded = true),
            nextAlarm = "Wake up" to Instant.parse("2026-10-06T07:00:00Z"),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun TodayContentEmptyPreview() {
    KairoTheme { TodayContent(state = TodayUiState(firstName = "Aarav", loaded = true)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun TodayLoadingPreview() {
    KairoTheme { TodayContent(state = TodayUiState()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun CommandBarPreview() {
    KairoTheme { CommandBar(onSubmit = {}, onMic = {}) }
}
