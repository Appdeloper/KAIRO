package com.kairo.app.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Sends the typed or spoken sentence to the user's Cloudflare Worker, which talks to the model.
 * Never retries: a slow or failed call is handled by the facade falling back to LocalParser.
 */
class CloudParser(
    private val http: OkHttpClient,
    private val settings: suspend () -> AiSettings,
    private val snapshot: suspend () -> PlannerSnapshot,
    private val json: Json = WireJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CommandParser {

    override suspend fun parse(text: String): ParseResult {
        val config = settings()
        if (!config.isReady) return ParseResult.Failed(FailReason.NotConfigured)
        val snap = snapshot()
        val payload = ParseRequestDto(
            deviceToken = config.deviceToken.trim(),
            text = PiiStripper.strip(text).trim().take(MAX_TEXT),
            context = ContextBuilder.build(snap),
        )
        val request = Request.Builder()
            .url(config.baseUrl.trim().trimEnd('/') + "/parse")
            .post(json.encodeToString(ParseRequestDto.serializer(), payload).toRequestBody(JSON))
            .build()
        val mapper = ToolCallMapper(snap.state.today, snap.state.roles)
        return withContext(io) { execute(request, mapper) }
    }

    private fun execute(request: Request, mapper: ToolCallMapper): ParseResult = try {
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            when (response.code) {
                200 -> decode(body, mapper)
                401, 403 -> ParseResult.Failed(FailReason.Unauthorized)
                429 -> ParseResult.Failed(FailReason.QuotaExceeded(errorMessage(body)))
                else -> ParseResult.Failed(FailReason.Server(response.code))
            }
        }
    } catch (e: InterruptedIOException) {
        // Includes SocketTimeoutException and OkHttp's call timeout.
        ParseResult.Failed(FailReason.Timeout)
    } catch (e: IOException) {
        ParseResult.Failed(FailReason.Network)
    }

    private fun decode(body: String, mapper: ToolCallMapper): ParseResult = try {
        mapper.map(json.decodeFromString(ParseResponseDto.serializer(), body).calls)
    } catch (e: SerializationException) {
        ParseResult.Unclear(UnclearReason.INVALID_OUTPUT)
    } catch (e: IllegalArgumentException) {
        ParseResult.Unclear(UnclearReason.INVALID_OUTPUT)
    }

    private fun errorMessage(body: String): String? = try {
        json.decodeFromString(ErrorDto.serializer(), body).message?.trim()?.take(MAX_NOTICE)?.takeIf { it.isNotEmpty() }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_TEXT = 500
        private const val MAX_NOTICE = 160

        /** The whole round trip, connect to last byte, must fit in 4 s or we parse offline instead. */
        const val CALL_TIMEOUT_SECONDS = 4L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
