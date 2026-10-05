package com.kairo.app.ai

import com.kairo.app.domain.brief.BestGap
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.plan.MINUTES_PER_DAY
import com.kairo.app.domain.plan.Slot
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

sealed interface BriefFetch {
    data class Success(val brief: Brief) : BriefFetch
    data class Failed(val reason: FailReason) : BriefFetch
}

/**
 * Calls the Worker's /brief (same URL and token as /parse). Any failure is returned, never thrown:
 * the caller always has the local template to show instead.
 */
class BriefClient(
    http: OkHttpClient,
    private val settings: suspend () -> AiSettings,
    private val json: Json = WireJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    // A brief is worth waiting a little longer for than a parse, but never more than 6 s.
    private val http = http.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(snapshot: PlannerSnapshot, freeSlots: List<Slot>): BriefFetch {
        val config = settings()
        if (!config.isReady) return BriefFetch.Failed(FailReason.NotConfigured)
        val payload = BriefRequestDto(config.deviceToken.trim(), ContextBuilder.buildForBrief(snapshot, freeSlots))
        val request = Request.Builder()
            .url(config.baseUrl.trim().trimEnd('/') + "/brief")
            .post(json.encodeToString(BriefRequestDto.serializer(), payload).toRequestBody(JSON))
            .build()
        return withContext(io) { execute(request, freeSlots) }
    }

    private fun execute(request: Request, freeSlots: List<Slot>): BriefFetch = try {
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            when (response.code) {
                200 -> decode(body, freeSlots)
                401, 403 -> BriefFetch.Failed(FailReason.Unauthorized)
                429 -> BriefFetch.Failed(FailReason.QuotaExceeded(null))
                else -> BriefFetch.Failed(FailReason.Server(response.code))
            }
        }
    } catch (e: InterruptedIOException) {
        BriefFetch.Failed(FailReason.Timeout)
    } catch (e: IOException) {
        BriefFetch.Failed(FailReason.Network)
    }

    private fun decode(body: String, freeSlots: List<Slot>): BriefFetch = try {
        val dto = json.decodeFromString(BriefDto.serializer(), body)
        if (dto.greeting.isBlank() || dto.summary.isBlank()) {
            BriefFetch.Failed(FailReason.Server(INVALID_BODY))
        } else {
            BriefFetch.Success(
                Brief(
                    greeting = dto.greeting.trim(),
                    summary = dto.summary.trim(),
                    bestGap = dto.bestGap?.takeIf { it.isInside(freeSlots) }?.let { BestGap(it.startMinute, it.endMinute, it.suggestion.trim()) },
                    ifThenPlans = dto.ifThenPlans.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_PLANS),
                ),
            )
        }
    } catch (e: SerializationException) {
        BriefFetch.Failed(FailReason.Server(INVALID_BODY))
    } catch (e: IllegalArgumentException) {
        BriefFetch.Failed(FailReason.Server(INVALID_BODY))
    }

    /** The Worker checks this too; repeating it here means a bad gap can never reach the screen. */
    private fun BestGapDto.isInside(slots: List<Slot>) =
        startMinute in 0 until MINUTES_PER_DAY && endMinute > startMinute &&
            slots.any { startMinute >= it.startMinute && endMinute <= it.endMinute }

    companion object {
        const val TIMEOUT_SECONDS = 6L
        private const val MAX_PLANS = 3

        /** Pseudo status for a 200 whose body we couldn't use. */
        const val INVALID_BODY = -1
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
