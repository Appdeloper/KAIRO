package com.kairo.app.domain.brief

import com.kairo.app.domain.DayPart
import com.kairo.app.domain.plan.PlanState
import java.security.MessageDigest
import java.time.LocalDate

object PlanHasher {
    /**
     * Fingerprint of everything a brief talks about for [date]. The day part is included so a
     * morning brief isn't replayed in the evening ("Good morning" at 9 pm) even if nothing changed.
     */
    fun hash(state: PlanState, date: LocalDate, dayPart: DayPart): String {
        val blocks = state.blocksOn(date).sortedBy { it.id }.joinToString("|") { "b${it.id}:${it.title}:${it.startMinute}-${it.endMinute}" }
        val tasks = state.tasksOn(date).sortedBy { it.id }
            .joinToString("|") { "t${it.id}:${it.title}:${it.scheduledStartMinute}:${it.durationMinutes}:${it.status}" }
        val canonical = "$date#$dayPart#$blocks#$tasks"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_LENGTH)
    }

    private const val HASH_LENGTH = 24
}
