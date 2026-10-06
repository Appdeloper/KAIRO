package com.kairo.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.prefs.FocusSettings
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ai.AiSettings
import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.service.focus.PromotionStatus
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.alarms.AlarmRingScreen
import com.kairo.app.ui.alarms.AlarmRowUi
import com.kairo.app.ui.alarms.AlarmsContent
import com.kairo.app.ui.alarms.AlarmsUiState
import com.kairo.app.ui.briefing.BriefingContent
import com.kairo.app.ui.briefing.BriefingInput
import com.kairo.app.ui.briefing.BriefingUiState
import com.kairo.app.ui.briefing.OrbState
import com.kairo.app.ui.briefing.TranscriptUi
import com.kairo.app.ui.focus.FocusSessionContent
import com.kairo.app.ui.focus.FocusSettingsContent
import com.kairo.app.ui.focus.FocusStartContent
import com.kairo.app.ui.focus.FocusTarget
import com.kairo.app.ui.focus.NextStepContent
import com.kairo.app.ui.onboarding.OnboardingContent
import com.kairo.app.ui.settings.AlarmSettingsSection
import com.kairo.app.ui.settings.SettingsContent
import com.kairo.app.ui.tasks.TasksContent
import com.kairo.app.ui.tasks.TasksUiState
import com.kairo.app.ui.timetable.TimetableContent
import com.kairo.app.ui.today.PlanDiffContent
import com.kairo.app.ui.today.TodayContent
import com.kairo.app.ui.today.TodayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/** One image per screen state. Phone-sized: 393 x 851 dp at xhdpi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xhdpi")
class ScreenScreenshots {
    @get:Rule val rule = createComposeRule()

    private val brief = Brief(
        greeting = "Good morning, Aarav",
        summary = "3 things left today. Next up: DBMS lecture at 9:00 AM, in 25 min.",
        bestGap = BestGap(13 * 60 + 10, 15 * 60 - 10, "1 h 40 min free: good for deep work."),
        ifThenPlans = listOf("If DBMS lecture runs over, then shift the next task, not your break."),
    )
    private val input = BriefingInput("", {}, {}, micAvailable = true, listening = false, onMic = {}, onEditTranscript = {})
    private val alarmsState = AlarmsUiState(
        rows = listOf(
            Alarm(id = 1, label = "Wake up", hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS),
            Alarm(id = 2, label = "Gym", hour = 18, minute = 0, daysOfWeekMask = AlarmDays.EVERY_DAY, enabled = false),
        ).map { AlarmRowUi(it, AlarmPlans.resolve(it, null), null) },
        blocks = PreviewData.blocks,
        loaded = true,
    ).let { it.copy(next = it.rows.first().plan to java.time.Instant.now().plusSeconds(9 * 3600L + 20 * 60 + 30)) }
    private val brokenHealth = AlarmHealth(exactAlarms = false, fullScreen = true, notifications = true, batteryUnrestricted = false, alarmVolumeAudible = true, nextAlarm = null)

    private fun onboarding(step: com.kairo.app.ui.onboarding.OnboardingStep) = Shots.capture(rule, "onboarding_${step.name.lowercase()}") {
        OnboardingContent(
            PreviewData.roles, setOf(4L),
            AlarmHealth(exactAlarms = true, fullScreen = false, notifications = false, batteryUnrestricted = true, alarmVolumeAudible = true, nextAlarm = null),
            false, { _, _ -> }, { _, _ -> }, {}, {}, { _, _, _, _, _ -> }, startStep = step,
        )
    }

    @Test fun onboardingWelcome() = onboarding(com.kairo.app.ui.onboarding.OnboardingStep.WELCOME)
    @Test fun onboardingName() = onboarding(com.kairo.app.ui.onboarding.OnboardingStep.NAME)
    @Test fun onboardingTimes() = onboarding(com.kairo.app.ui.onboarding.OnboardingStep.TIMES)
    @Test fun onboardingLanes() = onboarding(com.kairo.app.ui.onboarding.OnboardingStep.LANES)
    @Test fun onboardingPermissions() = onboarding(com.kairo.app.ui.onboarding.OnboardingStep.PERMISSIONS)

    private val todayState = PreviewData.todayState.copy(nowMinute = 10 * 60 + 42, loaded = true)
    private val nextAlarm = "Wake up" to java.time.Instant.parse("2026-10-06T07:00:00Z")

    @Test fun today() = Shots.capture(rule, "today") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { TodayContent(state = todayState, nextAlarm = nextAlarm) }
            com.kairo.app.ui.today.CommandBar(onSubmit = {}, onMic = {})
        }
    }

    @Test fun todayFocus() = Shots.capture(rule, "today_focus") {
        TodayContent(
            state = todayState.copy(nowMinute = 16 * 60 + 10),
            focus = com.kairo.app.ui.today.FocusUi(com.kairo.app.ui.focus.PreviewFocusAccess.session, 17 * 60_000L + 32_000),
        )
    }

    @Test fun todayEmpty() = Shots.capture(rule, "today_empty") { TodayContent(state = TodayUiState(firstName = "Aarav", loaded = true)) }

    @Test fun todayLoading() = Shots.capture(rule, "today_loading") { TodayContent(state = TodayUiState()) }

    @Test fun todayLargeFont() = Shots.capture(rule, "today_font200", fontScale = 2f) { TodayContent(state = todayState, nextAlarm = nextAlarm) }

    @Test fun planDiff() = Shots.capture(rule, "plan_diff") {
        com.kairo.app.ui.design.components.SheetFrame { PlanDiffContent(diff = PreviewData.sampleDiffFull, today = PreviewData.today, onApply = {}, onCancel = {}) }
    }

    @Test fun planDiffAgenda() = Shots.capture(rule, "plan_diff_agenda") {
        com.kairo.app.ui.design.components.SheetFrame { PlanDiffContent(diff = PreviewData.sampleAgenda, today = PreviewData.today, onApply = {}, onCancel = {}) }
    }

    @Test fun planTimetable() = Shots.capture(rule, "plan_timetable") {
        com.kairo.app.ui.plan.PlanFrame(com.kairo.app.ui.plan.PlanSegment.TIMETABLE, {}) {
            TimetableContent(state = PreviewData.timetableState.copy(loaded = true), onSave = {}, onDelete = {}, today = 1)
        }
    }

    @Test fun planTimetableEmptyDay() = Shots.capture(rule, "plan_timetable_empty_day") {
        com.kairo.app.ui.plan.PlanFrame(com.kairo.app.ui.plan.PlanSegment.TIMETABLE, {}) {
            TimetableContent(state = PreviewData.timetableState.copy(loaded = true), onSave = {}, onDelete = {}, today = 6)
        }
    }

    @Test fun planTasks() = Shots.capture(rule, "plan_tasks") {
        com.kairo.app.ui.plan.PlanFrame(com.kairo.app.ui.plan.PlanSegment.TASKS, {}) {
            TasksContent(state = com.kairo.app.ui.tasks.TasksPreviewState.sample, onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {})
        }
    }

    @Test fun planTasksEmpty() = Shots.capture(rule, "plan_tasks_empty") {
        com.kairo.app.ui.plan.PlanFrame(com.kairo.app.ui.plan.PlanSegment.TASKS, {}) {
            TasksContent(state = TasksUiState(roles = PreviewData.roles, loaded = true), onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {})
        }
    }

    @Test fun alarms() = Shots.capture(rule, "alarms") {
        AlarmsContent(alarmsState, brokenHealth, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmsHealthOpen() = Shots.capture(rule, "alarms_health_open") {
        AlarmsContent(alarmsState, brokenHealth, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, healthExpanded = true)
    }

    @Test fun alarmsEmpty() = Shots.capture(rule, "alarms_empty") {
        AlarmsContent(AlarmsUiState(loaded = true), AlarmHealth.ALL_GOOD, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmsLargeFont() = Shots.capture(rule, "alarms_font200", fontScale = 2f) {
        AlarmsContent(alarmsState, brokenHealth, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmEditor() = Shots.capture(rule, "alarm_editor") {
        com.kairo.app.ui.design.components.SheetFrame { com.kairo.app.ui.alarms.AlarmEditorPreviewContent() }
    }

    @Test fun alarmRing() = Shots.capture(rule, "alarm_ring") { AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {}) }

    @Test fun alarmRingLargeFont() = Shots.capture(rule, "alarm_ring_font200", fontScale = 2f) {
        AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {})
    }

    @Test fun briefing() = Shots.capture(rule, "briefing") {
        BriefingContent(
            BriefingUiState(brief = brief, source = BriefSource.CLOUD, briefIsFinal = true, upcoming = PreviewData.todayState.entries.take(3)),
            OrbState.IDLE, 0.5f, false, 0, false, input, SnackbarHostState(), {}, {}, {},
        )
    }

    @Test fun briefingListening() = Shots.capture(rule, "briefing_listening") {
        BriefingContent(
            BriefingUiState(brief = brief.copy(bestGap = null), source = BriefSource.LOCAL, briefIsFinal = true, transcript = TranscriptUi.Confirming("gym skip kar")),
            OrbState.LISTENING, 0.7f, false, 0, false, input.copy(listening = true), SnackbarHostState(), {}, {}, {},
        )
    }

    @Test fun briefingOrbStates() = Shots.capture(rule, "briefing_orb_states") {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.androidxPad(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.kairo.app.ui.design.Spacing.sm),
        ) {
            OrbState.entries.forEach { st ->
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    com.kairo.app.ui.briefing.Orb(st, level = 0.6f, running = false, forceFallback = true, modifier = androidx.compose.ui.Modifier.androidxSize())
                    androidx.compose.material3.Text(st.name, style = com.kairo.app.ui.design.KairoTheme.type.titleMedium)
                }
            }
        }
    }

    @Test fun briefingLoading() = Shots.capture(rule, "briefing_loading") {
        BriefingContent(BriefingUiState(), OrbState.THINKING, 0f, false, 0, false, input.copy(micAvailable = false), SnackbarHostState(), {}, {}, {})
    }

    @Test fun focusSession() = Shots.capture(rule, "focus_session") {
        FocusSessionContent("Edit reel #12", Color(0xFFFF6FB7), 17 * 60_000L + 32_000, 0.3f, 10, {}, {}, {}, {}, laneName = "Content")
    }

    @Test fun focusNextStep() = Shots.capture(rule, "focus_next_step") {
        NextStepContent("Edit reel #12", "Add captions to the second half", {}, true, false, {}, {}, {})
    }

    @Test fun focusStart() = Shots.capture(rule, "focus_start_sheet") {
        com.kairo.app.ui.design.components.SheetFrame {
            FocusStartContent(
                target = FocusTarget(taskId = 2, title = "Edit reel #12", role = PreviewData.roles[3], startMinute = 16 * 60, endMinute = 16 * 60 + 45),
                initialMinutes = 45, running = null, onStart = {}, onOpenRunning = {}, onCancel = {},
            )
        }
    }

    private val settingsPrefs = UserPrefs(firstName = "Aarav", onboardingDone = true)

    @androidx.compose.runtime.Composable
    private fun settingsScreen(scroll: androidx.compose.foundation.ScrollState) = SettingsContent(
        scrollState = scroll,
        prefs = settingsPrefs,
        ai = AiSettings(),
        onSave = { _, _, _ -> },
        onSaveAi = {},
        lanes = { com.kairo.app.ui.onboarding.LaneEditor(PreviewData.roles, emptySet(), { _, _ -> }, { _, _ -> }) },
        alarms = { AlarmSettingsSection({}) },
        focus = { FocusSettingsContent(FocusSettings(), dndAccess = false, promotion = PromotionStatus.BLOCKED, onDefaultMinutes = {}, onSilenceChange = {}, onOpenPromotionSettings = {}) },
        health = {
            com.kairo.app.ui.design.components.GlassCard { com.kairo.app.ui.alarms.AlarmHealthRows(brokenHealth, {}) }
        },
        data = { com.kairo.app.ui.settings.DataSection({}, {}, {}) },
        about = { com.kairo.app.ui.settings.AboutSection("0.1.0-beta (12) · abc1234") },
    )

    @Test fun settings() = Shots.captureScrolling(rule, "settings") { settingsScreen(it) }

    @Test fun settingsLargeFont() = Shots.captureScrolling(rule, "settings_font200", fontScale = 2f) { settingsScreen(it) }
}

private fun androidx.compose.ui.Modifier.androidxPad() = this.then(androidx.compose.ui.Modifier.padding(com.kairo.app.ui.design.Spacing.lg))
private fun androidx.compose.ui.Modifier.androidxSize() = this.then(androidx.compose.ui.Modifier.size(140.dp))
