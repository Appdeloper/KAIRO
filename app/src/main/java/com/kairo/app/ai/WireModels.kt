package com.kairo.app.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Wire format shared with proxy/src/validate.ts. Names only, never database ids. */
@Serializable
data class ContextItemDto(
    val title: String,
    val role: String?,
    val day: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val kind: String,
)

@Serializable
data class FreeSlotDto(val startMinute: Int, val endMinute: Int)

@Serializable
data class PlannerContextDto(
    val firstName: String,
    val localDate: String,
    val localTime: String,
    val roles: List<String>,
    val items: List<ContextItemDto>,
    val freeSlots: List<FreeSlotDto> = emptyList(),
)

@Serializable
data class ParseRequestDto(val deviceToken: String, val text: String, val context: PlannerContextDto)

@Serializable
data class ToolCallDto(val name: String, val input: JsonElement)

@Serializable
data class ParseResponseDto(val calls: List<ToolCallDto>)

@Serializable
data class BriefRequestDto(val deviceToken: String, val context: PlannerContextDto)

@Serializable
data class BestGapDto(val startMinute: Int, val endMinute: Int, val suggestion: String)

@Serializable
data class BriefDto(val greeting: String, val summary: String, val bestGap: BestGapDto? = null, val ifThenPlans: List<String> = emptyList())

@Serializable
data class ErrorDto(val error: String? = null, val message: String? = null)

/** Lenient on unknown keys (the Worker may add fields), strict on types. */
val WireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
