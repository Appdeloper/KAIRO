package com.kairo.app.ai

import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.Fixtures
import com.kairo.app.domain.plan.TargetRef
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallMapperTest {
    private val today = Fixtures.MONDAY
    private val mapper = ToolCallMapper(today, Fixtures.ROLES)

    private fun call(name: String, input: String) = ToolCallDto(name, WireJson.parseToJsonElement(input))
    private fun map(vararg calls: ToolCallDto) = mapper.map(calls.toList())
    private fun parsed(vararg calls: ToolCallDto) = (map(*calls) as ParseResult.Parsed).commands
    private val invalid = ParseResult.Unclear(UnclearReason.INVALID_OUTPUT)

    private fun addTask(
        title: String = "\"Client call\"",
        date: String = "\"tomorrow\"",
        start: String = "1080",
        duration: String = "null",
        role: String = "\"Client\"",
        deadline: String = "null",
        priority: String = "null",
    ) = call(
        "add_task",
        """{"title":$title,"date":$date,"start_minute":$start,"duration_minutes":$duration,"role":$role,"deadline":$deadline,"priority":$priority}""",
    )

    // ---- every tool, valid ----

    @Test
    fun addTask_allFields() {
        assertEquals(
            listOf(Command.AddTask("Client call", today.plusDays(1), 1080, null, Fixtures.CLIENT.id, null, 3)),
            parsed(addTask()),
        )
        assertEquals(
            Command.AddTask("Logo", null, null, 120, Fixtures.CLIENT.id, LocalDateOf("2026-10-07"), 1),
            parsed(addTask(title = "\" Logo \"", date = "null", start = "null", duration = "120", role = "\"client\"", deadline = "\"2026-10-07\"", priority = "1")).single(),
        )
    }

    @Test
    fun addTask_unknownRoleLeavesItToRoleGuesser() {
        assertEquals(null, (parsed(addTask(role = "\"Gym bro\"")).single() as Command.AddTask).roleId)
    }

    @Test
    fun moveSkipCompleteQuery() {
        assertEquals(
            Command.MoveBlock(TargetRef.ByName("Client call"), today, 1140),
            parsed(call("move_block", """{"target":"Client call","date":"today","start_minute":1140}""")).single(),
        )
        assertEquals(
            Command.SkipBlock(TargetRef.ByName("Gym"), null),
            parsed(call("skip_block", """{"target":"Gym","date":null}""")).single(),
        )
        assertEquals(
            Command.CompleteTask(TargetRef.ByName("DBMS assignment")),
            parsed(call("complete_task", """{"target":"DBMS assignment"}""")).single(),
        )
        assertEquals(
            Command.QueryDay(today.plusDays(1), nextOnly = false),
            parsed(call("query_day", """{"date":"tomorrow","next_only":false}""")).single(),
        )
    }

    @Test
    fun brainDump() {
        val dump = call(
            "brain_dump",
            """{"tasks":[
              {"title":"Edit reel","date":null,"start_minute":null,"duration_minutes":null,"role":"Content","deadline":null,"priority":null},
              {"title":"Send invoice","date":null,"start_minute":null,"duration_minutes":30,"role":"Client","deadline":null,"priority":2}
            ]}""",
        )
        val items = (parsed(dump).single() as Command.BrainDump).items
        assertEquals(listOf("Edit reel", "Send invoice"), items.map { it.title })
        assertEquals(listOf(Fixtures.CONTENT.id, Fixtures.CLIENT.id), items.map { it.roleId })
    }

    @Test
    fun twoCallsBecomeTwoCommands() {
        val commands = parsed(addTask(), call("skip_block", """{"target":"Gym","date":"today"}"""))
        assertEquals(2, commands.size)
    }

    @Test
    fun unclearReasons() {
        assertEquals(ParseResult.Unclear(UnclearReason.LECTURE_FIXED), map(call("unclear", """{"reason":"lecture_fixed"}""")))
        assertEquals(ParseResult.Unclear(UnclearReason.NOT_A_COMMAND), map(call("unclear", """{"reason":"not_a_planner_command"}""")))
        assertEquals(ParseResult.Unclear(UnclearReason.AMBIGUOUS), map())
        // An unclear call next to a valid one still means "don't act".
        assertEquals(ParseResult.Unclear(UnclearReason.AMBIGUOUS), map(addTask(), call("unclear", """{"reason":"ambiguous"}""")))
    }

    // ---- invalid and missing fields ----

    @Test
    fun invalidValuesMakeTheWholeReplyUnclear() {
        val cases: List<ToolCallDto> = listOf(
            addTask(start = "1440"),
            addTask(start = "-5"),
            addTask(start = "\"1080\""),
            addTask(start = "10.5"),
            addTask(duration = "0"),
            addTask(duration = "-30"),
            addTask(date = "\"2026-13-40\""),
            addTask(date = "\"next week\""),
            addTask(date = "\"2030-01-01\""),
            addTask(deadline = "\"soon\""),
            addTask(priority = "7"),
            addTask(title = "\"   \""),
            addTask(title = "null"),
            addTask(title = "\"" + "x".repeat(200) + "\""),
            call("move_block", """{"target":"Gym","date":null,"start_minute":null}"""),
            call("query_day", """{"date":"today","next_only":"yes"}"""),
            call("brain_dump", """{"tasks":[]}"""),
            call("brain_dump", """{"tasks":"Edit reel"}"""),
            call("delete_all_tasks", """{}"""),
            call("skip_block", """["Gym"]"""),
        )
        for (bad in cases) assertEquals("$bad", invalid, map(bad))
        // One bad call poisons the batch: nothing half-applied.
        assertEquals(invalid, map(addTask(), addTask(start = "2000")))
    }

    @Test
    fun missingFieldsAreInvalid() {
        val cases = listOf(
            call("add_task", """{"title":"Gym","date":"today","start_minute":1080,"duration_minutes":null,"role":null,"deadline":null}"""),
            call("move_block", """{"target":"Gym","start_minute":1140}"""),
            call("skip_block", """{"date":"today"}"""),
            call("complete_task", """{}"""),
            call("query_day", """{"date":"today"}"""),
            call("brain_dump", """{}"""),
        )
        for (bad in cases) assertEquals("$bad", invalid, map(bad))
    }

    @Suppress("TestFunctionName")
    private fun LocalDateOf(iso: String) = java.time.LocalDate.parse(iso)

    @Suppress("unused")
    private fun JsonElement.debug() = toString()
}
