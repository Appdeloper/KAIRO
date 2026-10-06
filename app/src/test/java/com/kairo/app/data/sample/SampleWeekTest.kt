package com.kairo.app.data.sample

import com.kairo.app.alarm.AlarmDays
import com.kairo.app.data.local.Role
import com.kairo.app.data.local.TaskStatus
import com.kairo.app.domain.BlockValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SampleWeekTest {
    private val roles = listOf(
        Role(1, "College", "#38D9F5", 360), Role(2, "Intern", "#9B8CFF", 240),
        Role(3, "Client", "#3EE8A8", 120), Role(4, "Content", "#FF6FB7", 60),
    )
    private val today = LocalDate.of(2026, 10, 6)
    private val sample = SampleWeek.build(today, roles, nowMillis = 0)

    @Test fun everyLaneHasTasks() {
        assertEquals(setOf(1L, 2L, 3L, 4L), sample.tasks.map { it.roleId }.toSet())
    }

    @Test fun blocksAreValidAndCoverTheWorkWeek() {
        sample.blocks.forEach { assertNull(it.title, BlockValidation.validate(it.title, it.dayOfWeek, it.startMinute, it.endMinute)) }
        assertEquals((1..5).toSet(), sample.blocks.map { it.dayOfWeek }.toSet())
    }

    @Test fun hasTodayUpcomingUnscheduledAndDoneTasks() {
        val day = today.toEpochDay()
        assertTrue(sample.tasks.any { it.scheduledEpochDay == day && it.status == TaskStatus.SCHEDULED })
        assertTrue(sample.tasks.any { (it.scheduledEpochDay ?: 0) > day })
        assertTrue(sample.tasks.any { it.scheduledEpochDay == null })
        assertTrue(sample.tasks.any { it.status == TaskStatus.DONE })
    }

    @Test fun todaysTasksDontOverlap() {
        val slots = sample.tasks.filter { it.scheduledEpochDay == today.toEpochDay() }
            .map { it.scheduledStartMinute!! until it.scheduledStartMinute!! + it.durationMinutes }
            .sortedBy { it.first }
        slots.zipWithNext().forEach { (a, b) -> assertTrue("$a overlaps $b", a.last < b.first) }
    }

    @Test fun oneWeekdayWakeAlarm() {
        assertEquals(AlarmDays.WEEKDAYS, sample.alarm.daysOfWeekMask)
        assertEquals(7, sample.alarm.hour)
    }
}
