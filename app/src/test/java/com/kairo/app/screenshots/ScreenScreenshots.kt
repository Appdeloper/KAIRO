package com.kairo.app.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
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
    )
    private val brokenHealth = AlarmHealth(exactAlarms = false, fullScreen = true, notifications = true, batteryUnrestricted = false, alarmVolumeAudible = true, nextAlarm = null)

    @Test fun onboarding() = Shots.capture(rule, "onboarding") { OnboardingContent(onFinish = { _, _, _, _ -> }) }

    @Test fun today() = Shots.capture(rule, "today") { TodayContent(state = PreviewData.todayState, onTaskClick = {}) }

    @Test fun todayEmpty() = Shots.capture(rule, "today_empty") { TodayContent(state = TodayUiState(firstName = "Aarav"), onTaskClick = {}) }

    @Test fun todayLargeFont() = Shots.capture(rule, "today_font200", fontScale = 2f) { TodayContent(state = PreviewData.todayState, onTaskClick = {}) }

    @Test fun planDiff() = Shots.capture(rule, "plan_diff") { PlanDiffContent(diff = PreviewData.sampleDiff, today = PreviewData.today, onApply = {}, onCancel = {}) }

    @Test fun planDiffAgenda() = Shots.capture(rule, "plan_diff_agenda") { PlanDiffContent(diff = PreviewData.sampleAgenda, today = PreviewData.today, onApply = {}, onCancel = {}) }

    @Test fun timetable() = Shots.capture(rule, "timetable") { TimetableContent(state = PreviewData.timetableState, onSave = {}, onDelete = {}) }

    @Test fun tasks() = Shots.capture(rule, "tasks") { TasksContent(state = PreviewData.tasksState, onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {}) }

    @Test fun tasksEmpty() = Shots.capture(rule, "tasks_empty") { TasksContent(state = TasksUiState(roles = PreviewData.roles), onAdd = {}, onPutOnToday = { _, _ -> }, onToggleDone = {}) }

    @Test fun alarms() = Shots.capture(rule, "alarms") {
        AlarmsContent(alarmsState, brokenHealth, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmsEmpty() = Shots.capture(rule, "alarms_empty") {
        AlarmsContent(AlarmsUiState(loaded = true), AlarmHealth.ALL_GOOD, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmsLargeFont() = Shots.capture(rule, "alarms_font200", fontScale = 2f) {
        AlarmsContent(alarmsState, brokenHealth, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun alarmRing() = Shots.capture(rule, "alarm_ring") { AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {}) }

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

    @Test fun briefingLoading() = Shots.capture(rule, "briefing_loading") {
        BriefingContent(BriefingUiState(), OrbState.THINKING, 0f, false, 0, false, input.copy(micAvailable = false), SnackbarHostState(), {}, {}, {})
    }

    @Test fun focusSession() = Shots.capture(rule, "focus_session") {
        FocusSessionContent("Edit reel #12", Color(0xFFFF2E93), 17 * 60_000L + 32_000, 0.3f, 10, {}, {}, {}, {})
    }

    @Test fun focusNextStep() = Shots.capture(rule, "focus_next_step") {
        NextStepContent("Edit reel #12", "Add captions to the second half", {}, true, false, {}, {}, {})
    }

    @Test fun focusStart() = Shots.capture(rule, "focus_start_sheet") {
        FocusStartContent(
            target = FocusTarget(taskId = 2, title = "Edit reel #12", role = PreviewData.roles[3], startMinute = 16 * 60, endMinute = 16 * 60 + 45),
            initialMinutes = 45, running = null, onStart = {}, onOpenRunning = {}, onCancel = {},
        )
    }

    @Test fun settings() = Shots.capture(rule, "settings") {
        SettingsContent(
            prefs = UserPrefs(firstName = "Aarav", onboardingDone = true),
            roles = PreviewData.roles,
            ai = AiSettings(),
            onSave = { _, _, _ -> },
            onSaveAi = {},
            alarmSection = {
                AlarmSettingsSection(brokenHealth, {}, {})
                FocusSettingsContent(FocusSettings(), dndAccess = false, promotion = PromotionStatus.BLOCKED, onDefaultMinutes = {}, onSilenceChange = {}, onOpenPromotionSettings = {})
            },
        )
    }

    @Test fun settingsLargeFont() = Shots.capture(rule, "settings_font200", fontScale = 2f) {
        SettingsContent(
            prefs = UserPrefs(firstName = "Aarav", onboardingDone = true),
            roles = PreviewData.roles, ai = AiSettings(), onSave = { _, _, _ -> }, onSaveAi = {},
        )
    }
}
