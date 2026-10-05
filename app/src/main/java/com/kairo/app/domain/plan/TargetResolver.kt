package com.kairo.app.domain.plan

import com.kairo.app.data.local.TaskStatus
import java.time.LocalDate

sealed interface Resolution {
    data class Found(val target: Resolved) : Resolution
    data class Failed(val reason: Reason) : Resolution
}

/**
 * Turns "gym" into a concrete task or block. Prefers exact names, then partial ones, and things on
 * the day in question over the backlog. Refuses to guess between equally good matches: acting on
 * the wrong item is worse than asking.
 */
object TargetResolver {
    private const val MIN_PARTIAL_LENGTH = 3

    private enum class Scope { ON_DATE, ELSEWHERE }

    private data class Candidate(val target: Resolved, val title: String, val scope: Scope)

    fun resolve(ref: TargetRef, date: LocalDate, state: PlanState): Resolution = when (ref) {
        is TargetRef.TaskId -> state.tasks.firstOrNull { it.id == ref.id }
            ?.let { Resolution.Found(Resolved.TaskTarget(it)) }
            ?: Resolution.Failed(Reason.NotFound(ref.id.toString()))
        is TargetRef.BlockId -> state.fixedBlocks.firstOrNull { it.id == ref.id }
            ?.let { Resolution.Found(Resolved.BlockTarget(it)) }
            ?: Resolution.Failed(Reason.NotFound(ref.id.toString()))
        is TargetRef.ByName -> resolveName(ref.query, date, state)
    }

    private fun resolveName(query: String, date: LocalDate, state: PlanState): Resolution {
        val q = normalize(query)
        if (q.isEmpty()) return Resolution.Failed(Reason.NotFound(query))
        val ranked = candidates(date, state).mapNotNull { c -> matchTier(normalize(c.title), q)?.let { tier -> (tier to c.scope.ordinal) to c } }
        if (ranked.isEmpty()) return Resolution.Failed(Reason.NotFound(query))
        val best = ranked.minOf { it.first.first * 10 + it.first.second }
        val winners = ranked.filter { it.first.first * 10 + it.first.second == best }.map { it.second }
        return if (winners.size == 1) {
            Resolution.Found(winners.single().target)
        } else {
            Resolution.Failed(Reason.Ambiguous(query, winners.map { it.title }.distinct()))
        }
    }

    private fun candidates(date: LocalDate, state: PlanState): List<Candidate> {
        // Weekday blocks include skipped ones so "skip gym" twice can say "already skipped".
        val blocks = state.fixedBlocks.filter { it.dayOfWeek == date.dayOfWeek.value }
            .map { Candidate(Resolved.BlockTarget(it), it.title, Scope.ON_DATE) }
        val onDate = state.tasksOn(date).map { Candidate(Resolved.TaskTarget(it), it.title, Scope.ON_DATE) }
        val elsewhere = state.tasks
            .filter { it.scheduledEpochDay != date.toEpochDay() && it.status in setOf(TaskStatus.TODO, TaskStatus.SCHEDULED) }
            .map { Candidate(Resolved.TaskTarget(it), it.title, Scope.ELSEWHERE) }
        return blocks + onDate + elsewhere
    }

    /** 0 = exact, 1 = one name contains the other, null = no match. */
    private fun matchTier(title: String, query: String): Int? = when {
        title == query -> 0
        query.length >= MIN_PARTIAL_LENGTH && title.contains(query) -> 1
        title.length >= MIN_PARTIAL_LENGTH && query.contains(title) -> 1
        else -> null
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
}
