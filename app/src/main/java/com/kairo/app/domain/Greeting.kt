package com.kairo.app.domain

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

object Greeting {
    /** Boundaries chosen for a student day: "night" starts late because evenings are work time. */
    fun dayPartFor(minuteOfDay: Int): DayPart = when (minuteOfDay) {
        in 5 * 60 until 12 * 60 -> DayPart.MORNING
        in 12 * 60 until 17 * 60 -> DayPart.AFTERNOON
        in 17 * 60 until 22 * 60 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }
}
