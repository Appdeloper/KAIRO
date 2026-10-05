package com.kairo.app.domain.brief

import com.kairo.app.domain.DayPart
import com.kairo.app.domain.plan.BlockSkipKey
import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.Fixtures.LECTURE_3PM
import com.kairo.app.domain.plan.Fixtures.MONDAY
import com.kairo.app.domain.plan.Fixtures.TUESDAY
import com.kairo.app.domain.plan.Fixtures.h
import com.kairo.app.domain.plan.Fixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BriefCacheTest {
    private val brief = Brief("Hi", "Summary", null, emptyList())
    private val today = BriefKey(MONDAY.toEpochDay(), "abc")

    @Test
    fun samePlanHashReusesTheCachedBrief() {
        assertEquals(BriefDecision.UseCached(brief), BriefCachePolicy.decide(today, CachedBrief(today, brief), lastAttempt = today, aiReady = true))
    }

    @Test
    fun changedPlanHashRefreshes() {
        val changed = today.copy(planHash = "def")
        assertEquals(BriefDecision.FetchCloud, BriefCachePolicy.decide(changed, CachedBrief(today, brief), lastAttempt = today, aiReady = true))
    }

    @Test
    fun newDayRefreshes() {
        val tomorrow = BriefKey(TUESDAY.toEpochDay(), "abc")
        assertEquals(BriefDecision.FetchCloud, BriefCachePolicy.decide(tomorrow, CachedBrief(today, brief), lastAttempt = today, aiReady = true))
    }

    @Test
    fun atMostOneCloudFetchPerPlanVersion() {
        val changed = today.copy(planHash = "def")
        assertEquals(BriefDecision.LocalOnly, BriefCachePolicy.decide(changed, CachedBrief(today, brief), lastAttempt = changed, aiReady = true))
        assertEquals(BriefDecision.FetchCloud, BriefCachePolicy.decide(changed, cached = null, lastAttempt = null, aiReady = true))
    }

    @Test
    fun aiOffNeverUsesTheCloudOrItsCache() {
        assertEquals(BriefDecision.LocalOnly, BriefCachePolicy.decide(today, CachedBrief(today, brief), lastAttempt = null, aiReady = false))
    }

    @Test
    fun planHashTracksTodaysPlanOnly() {
        val gym = task(1, "Gym", 60, MONDAY, h(18))
        val base = Fixtures.state(blocks = listOf(LECTURE_3PM), tasks = listOf(gym))
        val hash = PlanHasher.hash(base, MONDAY, DayPart.MORNING)

        assertEquals(hash, PlanHasher.hash(base.copy(nowMinute = h(10)), MONDAY, DayPart.MORNING))
        assertNotEquals(hash, PlanHasher.hash(base.withTask(gym.copy(scheduledStartMinute = h(19))), MONDAY, DayPart.MORNING))
        assertNotEquals(hash, PlanHasher.hash(base.withSkip(BlockSkipKey(LECTURE_3PM.id, MONDAY.toEpochDay())), MONDAY, DayPart.MORNING))
        assertNotEquals(hash, PlanHasher.hash(base, MONDAY, DayPart.EVENING))
        // Tomorrow's tasks don't affect today's brief.
        assertEquals(hash, PlanHasher.hash(base.withTask(task(2, "Tomorrow thing", 30, TUESDAY, h(9))), MONDAY, DayPart.MORNING))
    }
}
