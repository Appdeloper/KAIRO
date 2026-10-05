package com.kairo.app.ai

import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.Slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class BriefClientTest {
    private val slots = listOf(Slot(Fixtures.h(10, 10), Fixtures.h(14, 50)))
    private val snapshot = PlannerSnapshot(aiState(), "Aarav 98765 43210")

    private fun client(http: FakeHttp, settings: AiSettings = READY) = BriefClient(http.client, { settings })

    private fun briefJson(gap: String) = """{"greeting":"Good morning, Aarav.","summary":"DBMS at three.","bestGap":$gap,"ifThenPlans":["If DBMS runs late, then push gym."]}"""

    @Test
    fun success_withBestGap() = runBlocking {
        val http = FakeHttp { respond(it, 200, briefJson("""{"startMinute":620,"endMinute":890,"suggestion":"Write the report."}""")) }
        val result = client(http).fetch(snapshot, slots) as BriefFetch.Success
        assertEquals("Good morning, Aarav.", result.brief.greeting)
        assertEquals(BestGap(620, 890, "Write the report."), result.brief.bestGap)
        assertEquals(listOf("If DBMS runs late, then push gym."), result.brief.ifThenPlans)
        assertEquals("https://kairo.example.workers.dev/brief", http.requests.single().url.toString())
    }

    @Test
    fun success_withNullBestGap_andGapOutsideFreeSlotsIsDropped() = runBlocking {
        val nullGap = client(FakeHttp { respond(it, 200, briefJson("null")) }).fetch(snapshot, slots) as BriefFetch.Success
        assertNull(nullGap.brief.bestGap)
        val badGap = client(FakeHttp { respond(it, 200, briefJson("""{"startMinute":900,"endMinute":960,"suggestion":"x"}""")) })
            .fetch(snapshot, slots) as BriefFetch.Success
        assertNull("a gap over the lecture is never shown", badGap.brief.bestGap)
    }

    @Test
    fun requestHasTodayOnlyFreeSlotsAndNoPii() = runBlocking {
        val http = FakeHttp { respond(it, 200, briefJson("null")) }
        client(http).fetch(snapshot, slots)
        val body = http.lastBody()
        val context = WireJson.parseToJsonElement(body).jsonObject["context"]!!.jsonObject
        assertEquals("Aarav [phone]", context["firstName"]!!.jsonPrimitive.content)
        assertTrue(context["items"]!!.jsonArray.all { it.jsonObject["day"]!!.jsonPrimitive.content == "today" })
        assertEquals(1, context["freeSlots"]!!.jsonArray.size)
        assertFalse(body.contains("98765"))
    }

    @Test
    fun timeoutErrorsAndGarbageAreFailuresNotCrashes() = runBlocking {
        assertEquals(BriefFetch.Failed(FailReason.Timeout), client(FakeHttp { throw SocketTimeoutException() }).fetch(snapshot, slots))
        assertEquals(BriefFetch.Failed(FailReason.Network), client(FakeHttp { throw UnknownHostException() }).fetch(snapshot, slots))
        assertEquals(BriefFetch.Failed(FailReason.Server(502)), client(FakeHttp { respond(it, 502, "{}") }).fetch(snapshot, slots))
        assertEquals(BriefFetch.Failed(FailReason.QuotaExceeded(null)), client(FakeHttp { respond(it, 429, "{}") }).fetch(snapshot, slots))
        assertEquals(BriefFetch.Failed(FailReason.Unauthorized), client(FakeHttp { respond(it, 401, "{}") }).fetch(snapshot, slots))
        for (garbage in listOf("nope", """{"greeting":"","summary":"x"}""", """{"summary":"no greeting"}""")) {
            assertEquals(garbage, BriefFetch.Failed(FailReason.Server(BriefClient.INVALID_BODY)), client(FakeHttp { respond(it, 200, garbage) }).fetch(snapshot, slots))
        }
    }

    @Test
    fun notConfiguredSkipsTheNetwork() = runBlocking {
        val http = FakeHttp { error("must not be called") }
        assertEquals(BriefFetch.Failed(FailReason.NotConfigured), client(http, AiSettings()).fetch(snapshot, slots))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun timeoutIsSixSeconds() {
        assertEquals(6L, BriefClient.TIMEOUT_SECONDS)
    }
}
