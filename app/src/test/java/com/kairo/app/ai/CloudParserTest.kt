package com.kairo.app.ai

import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.TargetRef
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CloudParserTest {
    private val twoCalls = """{"calls":[
        {"name":"add_task","input":{"title":"Client call","date":"tomorrow","start_minute":1080,"duration_minutes":null,"role":"Client","deadline":null,"priority":null}},
        {"name":"skip_block","input":{"target":"Gym","date":"today"}}
    ]}"""

    @Test
    fun success_postsToParseAndMapsCalls() = runBlocking {
        val http = FakeHttp { respond(it, 200, twoCalls) }
        val result = cloudParser(http).parse("kal 6 baje client call add kar aur gym skip kar") as ParseResult.Parsed
        assertEquals("https://kairo.example.workers.dev/parse", http.requests.single().url.toString())
        assertEquals(2, result.commands.size)
        assertEquals(Command.SkipBlock(TargetRef.ByName("Gym"), aiState().today), result.commands[1])
    }

    @Test
    fun requestCarriesStrippedTextAndNamesButNoIds() = runBlocking {
        val http = FakeHttp { respond(it, 200, twoCalls) }
        cloudParser(http).parse("call rohan 98765 43210 or mail rohan@x.com")
        val body = http.lastBody()
        val json = WireJson.parseToJsonElement(body).jsonObject
        assertEquals("call rohan [phone] or mail [email]", json["text"]!!.jsonPrimitive.content)
        assertFalse(body.contains("98765"))
        assertFalse(body.contains("\"id\""))
        assertTrue(body.contains("\"kind\":\"lecture\""))
        assertTrue(body.contains("\"title\":\"Gym\""))
    }

    @Test
    fun failuresAreReportedForTheFacadeToFallBack() = runBlocking {
        val quota = FakeHttp { respond(it, 429, """{"error":"quota_exceeded","message":"Aaj ka AI quota khatam ho gaya"}""") }
        assertEquals(ParseResult.Failed(FailReason.QuotaExceeded("Aaj ka AI quota khatam ho gaya")), cloudParser(quota).parse("x"))
        assertEquals(ParseResult.Failed(FailReason.Unauthorized), cloudParser(FakeHttp { respond(it, 401, "{}") }).parse("x"))
        assertEquals(ParseResult.Failed(FailReason.Server(504)), cloudParser(FakeHttp { respond(it, 504, "{}") }).parse("x"))
        assertEquals(ParseResult.Failed(FailReason.Timeout), cloudParser(FakeHttp { throw SocketTimeoutException("timeout") }).parse("x"))
        assertEquals(ParseResult.Failed(FailReason.Network), cloudParser(FakeHttp { throw UnknownHostException("offline") }).parse("x"))
    }

    @Test
    fun garbledBodiesAreUnclearNotCrashes() = runBlocking {
        for (body in listOf("not json", """{"calls":"nope"}""", """{"oops":true}""")) {
            assertEquals(body, ParseResult.Unclear(UnclearReason.INVALID_OUTPUT), cloudParser(FakeHttp { respond(it, 200, body) }).parse("x"))
        }
    }

    @Test
    fun notConfiguredNeverTouchesTheNetwork() = runBlocking {
        for (settings in listOf(AiSettings(), READY.copy(enabled = false), READY.copy(baseUrl = "http://insecure.example"), READY.copy(deviceToken = " "))) {
            val http = FakeHttp { error("must not be called") }
            assertEquals(ParseResult.Failed(FailReason.NotConfigured), cloudParser(http, settings).parse("x"))
            assertTrue(http.requests.isEmpty())
        }
    }
}
