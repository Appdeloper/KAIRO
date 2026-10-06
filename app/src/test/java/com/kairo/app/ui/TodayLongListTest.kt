package com.kairo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.data.local.Task
import com.kairo.app.domain.TimelineEntry
import com.kairo.app.ui.design.KairoTheme
import com.kairo.app.ui.today.TodayContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A 50-item day stays a lazy list: only what's on screen is composed, so scrolling cost doesn't grow
 * with the plan. (Frame timing itself needs a real device; see docs/BETA_TEST_GUIDE.md.)
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h851dp-xhdpi")
class TodayLongListTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fiftyItemsComposeLazily() {
        val role = PreviewData.roles.first()
        val entries = (0 until 50).map { i ->
            TimelineEntry.TaskEntry(
                Task(id = 1000L + i, title = "Item $i", roleId = role.id, durationMinutes = 15, scheduledEpochDay = PreviewData.today.toEpochDay(), scheduledStartMinute = 7 * 60 + i * 20, createdAt = 0),
                role,
            )
        }
        val state = PreviewData.todayState.copy(entries = entries, nowMinute = 6 * 60, loaded = true)
        rule.mainClock.autoAdvance = false
        rule.setContent { KairoTheme { Box(Modifier.fillMaxSize()) { TodayContent(state = state) } } }
        rule.mainClock.advanceTimeBy(1_200)

        val composed = (0 until 50).count { i ->
            rule.onAllNodes(hasText("Item $i", substring = false)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertTrue("expected only visible items to be composed, got $composed", composed in 1..20)

    }
}
