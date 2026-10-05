package com.kairo.app.domain.brief

import com.kairo.app.data.local.TaskStatus
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.plan.BlockSkipKey
import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.Fixtures.LECTURE_3PM
import com.kairo.app.domain.plan.Fixtures.MONDAY
import com.kairo.app.domain.plan.Fixtures.block
import com.kairo.app.domain.plan.Fixtures.h
import com.kairo.app.domain.plan.Fixtures.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plain, inspectable wording so the tests check what the builder decided, not how it's phrased. */
object FakeBriefStrings : BriefStrings {
    override fun greeting(dayPart: DayPart, firstName: String) = "$dayPart:$firstName"
    override fun emptyDay(dayPart: DayPart) = "empty:$dayPart"
    override fun summary(remainingCount: Int, nextTitle: String, nextStartMinute: Int, minutesUntil: Int) =
        "summary:$remainingCount:$nextTitle:$nextStartMinute:$minutesUntil"
    override fun gapSuggestion(gapMinutes: Int) = "gap:$gapMinutes"
    override fun ifThen(nextTitle: String) = "if:$nextTitle"
}

class LocalBriefBuilderTest {
    private val dbms = block(1, "DBMS", h(9), h(10))
    private fun build(state: com.kairo.app.domain.plan.PlanState) = LocalBriefBuilder.build(state, "Aarav", FakeBriefStrings)

    @Test
    fun morning_countsItemsNamesNextAndFindsFirstGap() {
        val call = task(1, "Client call", 30, MONDAY, h(12))
        val brief = build(Fixtures.state(blocks = listOf(dbms, LECTURE_3PM), tasks = listOf(call), now = h(8)))
        assertEquals("MORNING:Aarav", brief.greeting)
        assertEquals("summary:3:DBMS:540:60", brief.summary)
        // 8:00 until 10 min before DBMS = 50 free minutes.
        assertEquals(BestGap(h(8), h(8, 50), "gap:50"), brief.bestGap)
        assertEquals(listOf("if:DBMS"), brief.ifThenPlans)
    }

    @Test
    fun afternoon_onlyCountsWhatIsStillAhead() {
        val brief = build(Fixtures.state(blocks = listOf(dbms, LECTURE_3PM), now = h(13)))
        assertEquals("AFTERNOON:Aarav", brief.greeting)
        assertEquals("summary:1:DBMS lecture:900:120", brief.summary)
        assertEquals(BestGap(h(13), h(14, 50), "gap:110"), brief.bestGap)
    }

    @Test
    fun evening_inProgressItemIsNextWithZeroMinutes_andGapNeedsThirtyMinutes() {
        val gym = task(2, "Gym", 60, MONDAY, h(18))
        val late = task(3, "Revision", 60, MONDAY, h(19, 30))
        val brief = build(Fixtures.state(tasks = listOf(gym, late), now = h(18, 30)))
        assertEquals("EVENING:Aarav", brief.greeting)
        assertEquals("summary:2:Gym:1080:0", brief.summary)
        // 19:10-19:20 is too short; the next real gap is after Revision.
        assertEquals(BestGap(h(20, 40), h(23), "gap:140"), brief.bestGap)
    }

    @Test
    fun emptyDay_isStillABrief() {
        val brief = build(Fixtures.state(now = h(10)))
        assertEquals("MORNING:Aarav", brief.greeting)
        assertEquals("empty:MORNING", brief.summary)
        assertEquals(BestGap(h(10), h(23), "gap:780"), brief.bestGap)
        assertEquals(emptyList<String>(), brief.ifThenPlans)
    }

    @Test
    fun doneTasksAndSkippedLecturesDontCount() {
        val done = task(1, "Done thing", 30, MONDAY, h(11), status = TaskStatus.DONE)
        val skipped = setOf(BlockSkipKey(LECTURE_3PM.id, MONDAY.toEpochDay()))
        val brief = build(Fixtures.state(blocks = listOf(LECTURE_3PM), tasks = listOf(done), skipped = skipped, now = h(10)))
        assertEquals("empty:MORNING", brief.summary)
    }

    @Test
    fun nightWithNothingLeft() {
        val brief = build(Fixtures.state(blocks = listOf(dbms), now = h(22, 30)))
        assertEquals("NIGHT:Aarav", brief.greeting)
        assertEquals("empty:NIGHT", brief.summary)
        // 22:30 to the 23:00 bedtime is exactly the 30-minute minimum, so it still counts.
        assertEquals(BestGap(h(22, 30), h(23), "gap:30"), brief.bestGap)
        assertNull(build(Fixtures.state(now = h(22, 40))).bestGap)
    }
}
