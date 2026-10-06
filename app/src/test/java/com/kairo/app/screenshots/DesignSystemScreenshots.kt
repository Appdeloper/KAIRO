package com.kairo.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.ui.PreviewData
import com.kairo.app.ui.design.Elevation
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.design.Lane
import com.kairo.app.ui.design.Spacing
import com.kairo.app.ui.design.components.Banner
import com.kairo.app.ui.design.components.BannerTone
import com.kairo.app.ui.design.components.ChoicePill
import com.kairo.app.ui.design.components.EmptyState
import com.kairo.app.ui.design.components.GlassCard
import com.kairo.app.ui.design.components.GlassLevel
import com.kairo.app.ui.design.components.KairoIconButton
import com.kairo.app.ui.design.components.KairoTextButton
import com.kairo.app.ui.design.components.ListRow
import com.kairo.app.ui.design.components.LoadingOrb
import com.kairo.app.ui.design.components.NowMarker
import com.kairo.app.ui.design.components.PermissionCard
import com.kairo.app.ui.design.components.PrimaryButton
import com.kairo.app.ui.design.components.ProgressRing
import com.kairo.app.ui.design.components.RoleChip
import com.kairo.app.ui.design.components.SecondaryButton
import com.kairo.app.ui.design.components.SectionHeader
import com.kairo.app.ui.design.components.SegmentedControl
import com.kairo.app.ui.design.components.SliderRow
import com.kairo.app.ui.design.components.StatusPill
import com.kairo.app.ui.design.components.TimelineItem
import com.kairo.app.ui.design.components.TimelineState
import com.kairo.app.ui.design.components.ToggleRow
import com.kairo.app.ui.design.components.Tone
import com.kairo.app.ui.design.components.TopBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A gallery of every design-system component, for visual review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h1600dp-xhdpi")
class DesignSystemScreenshots {
    @get:Rule val rule = createComposeRule()

    @Test fun controls() = Shots.capture(rule, "ds_controls") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            TopBar("Design system", subtitle = "Buttons, chips, rows")
            SectionHeader("Buttons")
            PrimaryButton("Apply", {})
            PrimaryButton("Add task", {}, icon = Icons.Outlined.Add)
            PrimaryButton("Disabled", {}, enabled = false)
            SecondaryButton("Start focus", {})
            Row { KairoTextButton("Cancel", {}); KairoIconButton(Icons.Outlined.Mic, "Speak", {}); KairoIconButton(Icons.Outlined.Mic, "Speak", {}, filled = true) }
            SectionHeader("Lanes")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { PreviewData.roles.forEachIndexed { i, r -> RoleChip(r.copy(colorHex = Lane.entries[i].hex), selected = i == 1) } }
            SectionHeader("Pills")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill("Armed", tone = Tone.SUCCESS, icon = Icons.Outlined.CheckCircle)
                StatusPill("Needs a fix", tone = Tone.WARNING, icon = Icons.Outlined.WarningAmber)
                StatusPill("Offline", icon = Icons.Outlined.CloudOff)
                StatusPill("Now", tone = Tone.MOMENT)
            }
            SegmentedControl(listOf("Timetable", "Tasks"), 0, {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { ChoicePill("15 min", false, {}); ChoicePill("25 min", true, {}); ChoicePill("45 min", false, {}) }
            SectionHeader("Rows")
            GlassCard {
                ListRow("Notifications", subtitle = "Needed for alarms and focus", icon = Icons.Outlined.Notifications, onClick = {}) { StatusPill("On", tone = Tone.SUCCESS) }
                ToggleRow("Shake twice to open the briefing", true, {}, subtitle = "Works while the screen is on")
                SliderRow("Sensitivity", "14.0", 0.4f, {})
            }
            SectionHeader("Surfaces")
            GlassCard { Text("Level 1 glass", style = KairoTheme.type.titleMedium) }
            GlassCard(level = GlassLevel.TWO, elevation = Elevation.RAISED) { Text("Level 2 raised") }
            GlassCard(level = GlassLevel.TWO, elevation = Elevation.GLOW) { Text("Glow") }
        }
    }

    @Test fun feedback() = Shots.capture(rule, "ds_feedback") {
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                ProgressRing(0.33f) { Text("1/3", style = KairoTheme.numbers.medium) }
                LoadingOrb(size = 96.dp)
            }
            TimelineItem("9:00", "10:00", "DBMS lecture", Lane.COLLEGE.color, laneLabel = "College", detail = "LH-204", locked = true, state = TimelineState.DONE, stateText = "Done")
            NowMarker("10:42")
            TimelineItem("11:00", "13:00", "OS lab", Lane.COLLEGE.color, laneLabel = "College", state = TimelineState.SKIPPED, stateText = "Skipped today", locked = true)
            TimelineItem("16:00", "16:45", "Edit reel #12", Lane.CONTENT.color, laneLabel = "Content", state = TimelineState.FOCUS, stateText = "Focusing · 17:32 left", nextStep = "Next: add captions")
            Banner(BannerTone.WARNING, "Alarms may ring late", body = "Allow exact alarms so they ring on time.", actionLabel = "Fix it", onAction = {})
            Banner(BannerTone.ERROR, "Couldn't reach your AI", body = "Using the offline planner for now.")
            PermissionCard(Icons.Outlined.Mic, "Microphone", "So you can talk to KAIRO. Only while the briefing is open.", granted = false, allowLabel = "Allow", allowedLabel = "Allowed", onAllow = {}, laterLabel = "Later", onLater = {})
            EmptyState("Nothing planned yet", "Add your timetable or load a sample week.", actionLabel = "Add your timetable", onAction = {}, secondaryLabel = "Load sample week", onSecondary = {})
        }
    }
}
