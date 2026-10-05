package com.kairo.app.ai

import com.kairo.app.domain.parser.LocalParser
import com.kairo.app.domain.parser.ParseContext
import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.TargetRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserFacadeTest {
    private val cloudCommand = Command.QueryDay(nextOnly = true)
    private var cloudCalls = 0

    private fun cloud(result: ParseResult) = CommandParser { cloudCalls++; result }
    private val local = LocalCommandParser { aiState() }
    private fun facade(cloudResult: ParseResult, settings: AiSettings = READY) = CommandParserFacade(cloud(cloudResult), local, { settings })

    @Test
    fun cloudSuccessIsUsed() = runBlocking {
        val outcome = facade(ParseResult.Parsed(listOf(cloudCommand))).parseDetailed("whatever")
        assertEquals(ParseOutcome(ParseResult.Parsed(listOf(cloudCommand)), ParseSource.CLOUD), outcome)
    }

    @Test
    fun timeoutFallsBackSilently() = runBlocking {
        val outcome = facade(ParseResult.Failed(FailReason.Timeout)).parseDetailed("gym skip kar")
        assertEquals(ParseSource.LOCAL, outcome.source)
        assertEquals(ParseResult.Parsed(listOf(Command.SkipBlock(TargetRef.ByName("gym")))), outcome.result)
        assertNull(outcome.notice)
    }

    @Test
    fun quotaFallsBackWithTheWorkersMessage() = runBlocking {
        val outcome = facade(ParseResult.Failed(FailReason.QuotaExceeded("Aaj ka quota khatam"))).parseDetailed("gym skip kar")
        assertEquals(ParseSource.LOCAL, outcome.source)
        assertEquals(ParseNotice.QuotaExceeded("Aaj ka quota khatam"), outcome.notice)
        assertEquals(ParseResult.Parsed(listOf(Command.SkipBlock(TargetRef.ByName("gym")))), outcome.result)
    }

    @Test
    fun cloudUnclearIsFinal() = runBlocking {
        val outcome = facade(ParseResult.Unclear(UnclearReason.NOT_A_COMMAND)).parseDetailed("gym skip kar")
        assertEquals(ParseOutcome(ParseResult.Unclear(UnclearReason.NOT_A_COMMAND), ParseSource.CLOUD), outcome)
    }

    @Test
    fun aiOffOrNotSetUpUsesOnlyTheLocalParser() = runBlocking {
        for (settings in listOf(READY.copy(enabled = false), AiSettings())) {
            cloudCalls = 0
            val outcome = facade(ParseResult.Parsed(listOf(cloudCommand)), settings).parseDetailed("aaj kya hai")
            assertEquals(0, cloudCalls)
            assertEquals(ParseSource.LOCAL, outcome.source)
            // Exactly what step 1.2's LocalParser produced for the same text.
            val s = aiState()
            val expected = LocalParser.parse("aaj kya hai", ParseContext(s.today, s.nowMinute, s.wakeMinute, s.sleepMinute))
            assertEquals(ParseResult.Parsed(listOf(expected!!)), outcome.result)
        }
    }

    @Test
    fun localNotUnderstoodIsUnclearFromLocal() = runBlocking {
        val outcome = facade(ParseResult.Failed(FailReason.Network)).parseDetailed("hmm")
        assertEquals(ParseOutcome(ParseResult.Unclear(UnclearReason.AMBIGUOUS), ParseSource.LOCAL), outcome)
    }
}
