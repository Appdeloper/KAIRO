package com.kairo.app.ai

import com.kairo.app.data.local.Role
import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.MINUTES_PER_DAY
import com.kairo.app.domain.plan.TargetRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Turns the model's tool calls into domain Commands. Model output is untrusted: every field is
 * checked, and one bad field makes the whole reply Unclear rather than half-applying it.
 */
class ToolCallMapper(private val today: LocalDate, private val roles: List<Role>) {

    private class InvalidOutput : Exception()

    fun map(calls: List<ToolCallDto>): ParseResult {
        if (calls.isEmpty()) return ParseResult.Unclear(UnclearReason.AMBIGUOUS)
        calls.firstOrNull { it.name == "unclear" }?.let { return ParseResult.Unclear(unclearReason(it.input)) }
        return try {
            ParseResult.Parsed(calls.map { toCommand(it) })
        } catch (e: InvalidOutput) {
            ParseResult.Unclear(UnclearReason.INVALID_OUTPUT)
        }
    }

    private fun toCommand(call: ToolCallDto): Command {
        val input = call.input as? JsonObject ?: invalid()
        return when (call.name) {
            "add_task" -> addTask(input)
            "move_block" -> {
                val date = date(input, "date")
                val start = minute(input, "start_minute")
                // A move with neither a day nor a time says nothing about where to go.
                if (date == null && start == null) invalid()
                Command.MoveBlock(TargetRef.ByName(text(input, "target")), date, start)
            }
            "skip_block" -> Command.SkipBlock(TargetRef.ByName(text(input, "target")), date(input, "date"))
            "complete_task" -> Command.CompleteTask(TargetRef.ByName(text(input, "target")))
            "query_day" -> Command.QueryDay(date(input, "date"), boolean(input, "next_only"))
            "brain_dump" -> {
                val tasks = input.require("tasks") as? JsonArray ?: invalid()
                if (tasks.isEmpty() || tasks.size > MAX_BRAIN_DUMP) invalid()
                Command.BrainDump(tasks.map { addTask(it as? JsonObject ?: invalid()) })
            }
            else -> invalid()
        }
    }

    private fun addTask(input: JsonObject) = Command.AddTask(
        title = text(input, "title"),
        date = date(input, "date"),
        startMinute = minute(input, "start_minute"),
        durationMinutes = nullableInt(input, "duration_minutes")?.also { if (it !in 1..MINUTES_PER_DAY) invalid() },
        // An unknown role name isn't an error: null lets RoleGuesser pick, and the preview shows it.
        roleId = nullableString(input, "role")?.let { name -> roles.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id },
        deadline = date(input, "deadline"),
        priority = nullableInt(input, "priority")?.also { if (it !in 1..4) invalid() } ?: DEFAULT_PRIORITY,
    )

    private fun unclearReason(input: JsonElement): UnclearReason =
        when (((input as? JsonObject)?.get("reason") as? JsonPrimitive)?.content) {
            "not_a_planner_command" -> UnclearReason.NOT_A_COMMAND
            "lecture_fixed" -> UnclearReason.LECTURE_FIXED
            else -> UnclearReason.AMBIGUOUS
        }

    // ---- field readers: absent key, wrong type or out-of-range value all mean invalid output ----

    private fun JsonObject.require(key: String): JsonElement = get(key) ?: invalid()

    private fun text(o: JsonObject, key: String): String {
        val p = o.require(key) as? JsonPrimitive ?: invalid()
        if (!p.isString) invalid()
        return p.content.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT } ?: invalid()
    }

    private fun nullableString(o: JsonObject, key: String): String? {
        val e = o.require(key)
        if (e is JsonNull) return null
        val p = e as? JsonPrimitive ?: invalid()
        if (!p.isString) invalid()
        return p.content.takeIf { it.isNotBlank() }
    }

    private fun nullableInt(o: JsonObject, key: String): Int? {
        val e = o.require(key)
        if (e is JsonNull) return null
        val p = e as? JsonPrimitive ?: invalid()
        if (p.isString) invalid()
        return p.intOrNull ?: invalid()
    }

    private fun minute(o: JsonObject, key: String): Int? =
        nullableInt(o, key)?.also { if (it !in 0 until MINUTES_PER_DAY) invalid() }

    private fun boolean(o: JsonObject, key: String): Boolean {
        val p = o.require(key) as? JsonPrimitive ?: invalid()
        if (p.isString) invalid()
        return p.booleanOrNull ?: invalid()
    }

    private fun date(o: JsonObject, key: String): LocalDate? = when (val raw = nullableString(o, key)?.trim()?.lowercase()) {
        null -> null
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        else -> try {
            LocalDate.parse(raw).also { if (it.isAfter(today.plusYears(1)) || it.isBefore(today.minusYears(1))) invalid() }
        } catch (e: DateTimeParseException) {
            invalid()
        }
    }

    private fun invalid(): Nothing = throw InvalidOutput()

    private companion object {
        const val MAX_TEXT = 120
        const val MAX_BRAIN_DUMP = 20
        const val DEFAULT_PRIORITY = 3
    }
}
