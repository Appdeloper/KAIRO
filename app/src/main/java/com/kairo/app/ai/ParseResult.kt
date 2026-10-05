package com.kairo.app.ai

import com.kairo.app.domain.plan.Command

enum class UnclearReason { NOT_A_COMMAND, AMBIGUOUS, LECTURE_FIXED, INVALID_OUTPUT }

sealed interface FailReason {
    data object NotConfigured : FailReason
    data object Timeout : FailReason
    data object Network : FailReason
    data object Unauthorized : FailReason
    data class QuotaExceeded(val message: String?) : FailReason
    data class Server(val code: Int) : FailReason
}

/** What any parser hands back. Commands still go through plan() and the PlanDiff preview. */
sealed interface ParseResult {
    data class Parsed(val commands: List<Command>) : ParseResult
    data class Unclear(val reason: UnclearReason) : ParseResult
    data class Failed(val reason: FailReason) : ParseResult
}

fun interface CommandParser {
    suspend fun parse(text: String): ParseResult
}
