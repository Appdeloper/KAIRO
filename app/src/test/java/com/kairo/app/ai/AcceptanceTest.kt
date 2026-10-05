package com.kairo.app.ai

import com.kairo.app.domain.plan.Change
import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.CommandExecutor
import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.PlanStore
import com.kairo.app.domain.plan.RemovedItem
import com.kairo.app.domain.plan.TargetRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** The three acceptance criteria for step 1.3, end to end from text to PlanDiff preview. */
class AcceptanceTest {
    private val executor = CommandExecutor(object : PlanStore {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = error("preview only")
        override suspend fun findTask(id: Long) = error("preview only")
        override suspend fun insertTask(task: com.kairo.app.data.local.Task) = error("preview only")
        override suspend fun updateTask(task: com.kairo.app.data.local.Task) = error("preview only")
        override suspend fun deleteTask(id: Long) = error("preview only")
        override suspend fun isBlockSkipped(key: com.kairo.app.domain.plan.BlockSkipKey) = error("preview only")
        override suspend fun addBlockSkip(key: com.kairo.app.domain.plan.BlockSkipKey) = error("preview only")
        override suspend fun removeBlockSkip(key: com.kairo.app.domain.plan.BlockSkipKey) = error("preview only")
    })

    private val workerReply = """{"calls":[
        {"name":"add_task","input":{"title":"Client call","date":"tomorrow","start_minute":1080,"duration_minutes":null,"role":"Client","deadline":null,"priority":null}},
        {"name":"skip_block","input":{"target":"Gym","date":"today"}}
    ]}"""

    @Test
    fun oneSentenceTwoCommandsOneDiff() = runBlocking {
        val facade = CommandParserFacade(cloudParser(FakeHttp { respond(it, 200, workerReply) }), LocalCommandParser { aiState() }, { READY })
        val result = facade.parse("kal 6 baje client call add kar aur gym skip kar") as ParseResult.Parsed
        val diff = executor.planAll(result.commands, aiState())

        assertEquals(2, diff.mutations.size)
        val added = (diff.mutations[0] as Change.Added).task
        assertEquals("Client call", added.title)
        assertEquals(Fixtures.TUESDAY.toEpochDay(), added.scheduledEpochDay)
        assertEquals(Fixtures.h(18), added.scheduledStartMinute)
        assertEquals(Fixtures.CLIENT.id, added.roleId)
        val skipped = (diff.mutations[1] as Change.Removed).item as RemovedItem.TaskUnscheduled
        assertEquals("Gym", skipped.before.title)
    }

    @Test
    fun airplaneModeStillSkipsGymOffline() = runBlocking {
        val offline = cloudParser(FakeHttp { throw UnknownHostException("airplane mode") })
        val facade = CommandParserFacade(offline, LocalCommandParser { aiState() }, { READY })
        val outcome = facade.parseDetailed("gym skip kar")
        assertEquals(ParseSource.LOCAL, outcome.source)
        val diff = executor.planAll((outcome.result as ParseResult.Parsed).commands, aiState())
        assertTrue((diff.mutations.single() as Change.Removed).item is RemovedItem.TaskUnscheduled)
    }

    @Test
    fun aiOffBehavesLikeStep12() = runBlocking {
        val http = FakeHttp { error("AI off must not use the network") }
        val facade = CommandParserFacade(cloudParser(http, READY.copy(enabled = false)), LocalCommandParser { aiState() }, { READY.copy(enabled = false) })
        val outcome = facade.parseDetailed("gym skip kar")
        assertEquals(ParseResult.Parsed(listOf(Command.SkipBlock(TargetRef.ByName("gym")))), outcome.result)
        assertTrue(http.requests.isEmpty())
    }
}
