package com.kairo.app.domain.plan

import java.time.LocalDate

/** How a command points at something on the plan. Voice gives names; UI taps give ids. */
sealed class TargetRef {
    data class ByName(val query: String) : TargetRef()
    data class TaskId(val id: Long) : TargetRef()
    data class BlockId(val id: Long) : TargetRef()
}

/**
 * Everything KAIRO can be asked to do. Parsers (local or LLM) only produce these; they never touch
 * the database. A null date means "today".
 */
sealed class Command {
    data class AddTask(
        val title: String,
        val date: LocalDate? = null,
        val startMinute: Int? = null,
        val durationMinutes: Int? = null,
        val roleId: Long? = null,
        val deadline: LocalDate? = null,
        val priority: Int = 3,
    ) : Command()

    data class MoveBlock(val target: TargetRef, val toDate: LocalDate? = null, val toStartMinute: Int? = null) : Command()
    data class SkipBlock(val target: TargetRef, val date: LocalDate? = null) : Command()
    data class CompleteTask(val target: TargetRef) : Command()
    data class QueryDay(val date: LocalDate? = null, val nextOnly: Boolean = false) : Command()
    data class BrainDump(val items: List<AddTask>) : Command()
}
