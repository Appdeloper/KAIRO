package com.kairo.app.ai

import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.PlanState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

val READY = AiSettings(enabled = true, baseUrl = "https://kairo.example.workers.dev/", deviceToken = "device-token-123456")

/** Monday 9:00 with a 3 pm lecture and an evening Gym task (from the scheduler fixtures). */
fun aiState(): PlanState = Fixtures.state(
    blocks = listOf(Fixtures.LECTURE_3PM),
    tasks = listOf(Fixtures.task(9, "Gym", 60, Fixtures.MONDAY, Fixtures.h(18))),
    now = Fixtures.h(9),
)

/** OkHttp client whose "network" is a lambda, so tests never open a socket. */
class FakeHttp(private val handler: (Request) -> Response) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.request().also { requests += it }.let(handler) }
        .build()

    fun lastBody(): String = Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()
}

fun respond(request: Request, code: Int, body: String): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message("test")
    .body(body.toResponseBody("application/json".toMediaType()))
    .build()

fun cloudParser(http: FakeHttp, settings: AiSettings = READY, state: PlanState = aiState()) = CloudParser(
    http = http.client,
    settings = { settings },
    snapshot = { PlannerSnapshot(state, "Aarav") },
)
