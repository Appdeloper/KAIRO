package com.kairo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.ai.AiSettings
import com.kairo.app.alarm.AlarmDays
import com.kairo.app.alarm.AlarmHealth
import com.kairo.app.alarm.AlarmPlans
import com.kairo.app.data.local.Alarm
import com.kairo.app.data.prefs.UserPrefs
import com.kairo.app.ui.alarms.AlarmRingScreen
import com.kairo.app.ui.alarms.AlarmRowUi
import com.kairo.app.ui.alarms.AlarmsContent
import com.kairo.app.ui.alarms.AlarmsUiState
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.ui.briefing.BriefingContent
import com.kairo.app.ui.briefing.BriefingInput
import com.kairo.app.ui.briefing.BriefingPreviewData
import com.kairo.app.ui.briefing.BriefingUiState
import com.kairo.app.ui.briefing.OrbState
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.focus.FocusSessionContent
import com.kairo.app.ui.focus.NextStepContent
import com.kairo.app.ui.onboarding.OnboardingContent
import com.kairo.app.ui.onboarding.OnboardingStep
import com.kairo.app.ui.settings.AboutSection
import com.kairo.app.ui.settings.AlarmSettingsSection
import com.kairo.app.ui.settings.DataSection
import com.kairo.app.ui.settings.SettingsContent
import com.kairo.app.ui.tasks.TasksContent
import com.kairo.app.ui.tasks.TasksPreviewState
import com.kairo.app.ui.today.TodayContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Every tappable thing on every screen: at least 48 × 48 dp of touch area and something TalkBack can
 * say. Run at 100% and 200% font so large-text users get the same guarantees.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h851dp-xhdpi")
class AccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val health = AlarmHealth(exactAlarms = false, fullScreen = true, notifications = true, batteryUnrestricted = false, alarmVolumeAudible = true, nextAlarm = null)
    private val alarms = AlarmsUiState(
        rows = listOf(
            Alarm(id = 1, label = "Wake up", hour = 7, minute = 0, daysOfWeekMask = AlarmDays.WEEKDAYS),
            Alarm(id = 2, label = "Gym", hour = 18, minute = 0, daysOfWeekMask = AlarmDays.EVERY_DAY, enabled = false),
        ).map { AlarmRowUi(it, AlarmPlans.resolve(it, null), null) },
        loaded = true,
    )
    private val input = BriefingInput("", {}, {}, micAvailable = true, listening = false, onMic = {}, onEditTranscript = {})

    private val screens: Map<String, @Composable () -> Unit> = linkedMapOf<String, @Composable () -> Unit>(
        "today" to { TodayContent(state = PreviewData.todayState.copy(nowMinute = 10 * 60 + 42, loaded = true)) },
        "today_empty" to { TodayContent(state = com.kairo.app.ui.today.TodayUiState(firstName = "Aarav", loaded = true), onAddTimetable = {}, onLoadSample = {}) },
        "tasks" to { TasksContent(TasksPreviewState.sample, {}, { _, _ -> }, {}) },
        "alarms" to { AlarmsContent(alarms, health, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, healthExpanded = true) },
        "alarm_ring" to { AlarmRingScreen(LocalTime.of(7, 0), "Wake up", "DBMS lecture", 5, 3, {}, {}) },
        "briefing" to {
            BriefingContent(
                BriefingUiState(brief = BriefingPreviewData.brief, source = BriefSource.LOCAL, briefIsFinal = true, upcoming = PreviewData.todayState.entries.take(3)),
                OrbState.IDLE, 0f, false, 0, false, input, SnackbarHostState(), {}, {}, {},
            )
        },
        "focus" to { FocusSessionContent("Edit reel #12", Color(0xFFFF6FB7), 17 * 60_000L, 0.3f, 0, {}, {}, {}, {}, laneName = "Content") },
        "next_step" to { NextStepContent("Edit reel #12", "", {}, true, false, {}, {}, {}) },
        "settings" to {
            SettingsContent(
                prefs = UserPrefs(firstName = "Aarav", onboardingDone = true), ai = AiSettings(), onSave = { _, _, _ -> }, onSaveAi = {},
                alarms = { AlarmSettingsSection({}) }, data = { DataSection({}, {}, {}) }, about = { AboutSection("0.1.0-beta (1) · local") },
            )
        },
    ) + OnboardingStep.entries.associate { step ->
        "onboarding_${step.name.lowercase()}" to @Composable {
            OnboardingContent(PreviewData.roles, emptySet(), health, false, { _, _ -> }, { _, _ -> }, {}, {}, { _, _, _, _, _ -> }, startStep = step)
        }
    }

    @Test fun touchTargetsAndLabelsAtNormalFont() = checkAll(1f)

    @Test fun touchTargetsAndLabelsAtDoubleFont() = checkAll(2f)

    private fun checkAll(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val problems = mutableListOf<String>()
        var current by mutableStateOf(screens.keys.first())
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KairoTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { androidx.compose.runtime.key(current) { screens.getValue(current)() } }
            }
        }
        screens.keys.forEach { name ->
            current = name
            // Publish the state change, then run frames so the new screen is really composed and laid out.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.mainClock.advanceTimeBy(1_200)
            val nodes = clickableNodes()
            // Guards the test itself: a screen with nothing tappable means the check didn't really run.
            if (nodes.isEmpty()) problems += "$name: no clickable nodes found"
            problems += nodes.mapNotNull { node -> problemWith(node)?.let { "$name: $it" } }
            println("a11y $name ${fontScale}x: ${nodes.size} clickable")
        }
        assertTrue("Accessibility problems at ${fontScale}x font:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun clickableNodes(): List<SemanticsNode> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun problemWith(node: SemanticsNode): String? {
        val density = node.layoutInfo.density.density
        val bounds = node.touchBoundsInRoot
        val minPx = 48 * density - 1
        val label = describe(node)
        if (node.config.getOrNull(SemanticsProperties.Disabled) != null) return null
        // Clipped by a scroll container, i.e. off screen: TalkBack scrolls to it, size can't be measured here.
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        return when {
            label.isBlank() -> "unlabelled clickable at $bounds"
            bounds.width < minPx || bounds.height < minPx ->
                "'$label' is ${(bounds.width / density).toInt()}x${(bounds.height / density).toInt()} dp"
            else -> null
        }
    }

    /** What TalkBack would read: own text/description, else the merged children's. */
    private fun describe(node: SemanticsNode): String {
        val own = listOfNotNull(
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            node.config.getOrNull(SemanticsProperties.EditableText)?.text,
            node.config.getOrNull(SemanticsActions.OnClick)?.label,
        ).joinToString(" ").trim()
        if (own.isNotEmpty()) return own
        return node.children.joinToString(" ") { describe(it) }.trim()
    }
}
