package com.kairo.app.ai

import com.kairo.app.domain.parser.LocalParser
import com.kairo.app.domain.parser.ParseContext
import com.kairo.app.domain.plan.PlanState

/** Adapts the offline LocalParser (unchanged since step 1.2) to the CommandParser interface. */
class LocalCommandParser(private val state: suspend () -> PlanState) : CommandParser {
    override suspend fun parse(text: String): ParseResult {
        val s = state()
        val command = LocalParser.parse(text, ParseContext(s.today, s.nowMinute, s.wakeMinute, s.sleepMinute))
        return if (command != null) ParseResult.Parsed(listOf(command)) else ParseResult.Unclear(UnclearReason.AMBIGUOUS)
    }
}
