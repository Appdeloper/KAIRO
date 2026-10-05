package com.kairo.app.domain.parser

import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.TargetRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LocalParserTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val ctx = ParseContext(today = monday, nowMinute = 9 * 60, wakeMinute = 7 * 60, sleepMinute = 23 * 60 + 30)
    private fun parse(s: String) = LocalParser.parse(s, ctx)
    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    // ---- the phrases from the spec ----

    @Test
    fun addXAt6pm() {
        assertEquals(Command.AddTask("Client call", startMinute = h(18)), parse("add client call at 6pm"))
        assertEquals(Command.AddTask("Edit reel 12", startMinute = h(18)), parse("Add edit reel 12 at 6 PM"))
    }

    @Test
    fun x6Baje() {
        assertEquals(Command.AddTask("Gym", startMinute = h(18)), parse("gym 6 baje"))
        assertEquals(Command.AddTask("Padhai", startMinute = h(18, 30)), parse("padhai saade chhe baje"))
    }

    @Test
    fun gymSkipKar() {
        assertEquals(Command.SkipBlock(TargetRef.ByName("gym")), parse("gym skip kar"))
        assertEquals(Command.SkipBlock(TargetRef.ByName("gym"), monday.plusDays(1)), parse("kal ka gym skip kar do"))
    }

    @Test
    fun skipGym() {
        assertEquals(Command.SkipBlock(TargetRef.ByName("gym")), parse("skip gym"))
        assertEquals(Command.SkipBlock(TargetRef.ByName("dbms lecture"), monday), parse("Skip DBMS lecture today"))
    }

    @Test
    fun whatsNext() {
        assertEquals(Command.QueryDay(nextOnly = true), parse("what's next"))
        assertEquals(Command.QueryDay(nextOnly = true), parse("What’s next?"))
        assertEquals(Command.QueryDay(nextOnly = true), parse("ab kya hai"))
    }

    @Test
    fun aajKyaHai() {
        assertEquals(Command.QueryDay(date = monday), parse("aaj kya hai"))
        assertEquals(Command.QueryDay(date = monday.plusDays(1)), parse("kal kya hai?"))
        assertEquals(Command.QueryDay(date = monday), parse("whats on today"))
    }

    @Test
    fun kal5BajeCallRakh() {
        assertEquals(Command.AddTask("Call", date = monday.plusDays(1), startMinute = h(17)), parse("kal 5 baje call rakh"))
    }

    // ---- every normalizer form reaches the parser ----

    @Test
    fun hinglishFractionsAndHints() {
        assertEquals(h(18, 45), (parse("meeting pone saat") as Command.AddTask).startMinute)
        assertEquals(h(17, 15), (parse("call sava paanch baje") as Command.AddTask).startMinute)
        assertEquals(h(13, 30), (parse("lunch dedh baje") as Command.AddTask).startMinute)
        assertEquals(h(14, 30), (parse("dhai baje standup") as Command.AddTask).startMinute)
        assertEquals(Command.AddTask("Run", date = monday.plusDays(1), startMinute = h(6)), parse("kal subah 6 baje run"))
        assertEquals(h(14), (parse("dopahar 2 baje client call") as Command.AddTask).startMinute)
        assertEquals(h(19), (parse("shaam 7 baje gym") as Command.AddTask).startMinute)
        assertEquals(h(22), (parse("raat 10 baje revision") as Command.AddTask).startMinute)
    }

    // ---- extras ----

    @Test
    fun durationIsPickedUpAndRemovedFromTitle() {
        assertEquals(Command.AddTask("Gym", startMinute = h(18), durationMinutes = 90), parse("gym 6 baje dedh ghanta"))
        assertEquals(Command.AddTask("Write report", startMinute = h(16), durationMinutes = 45), parse("add write report at 4pm for 45 min"))
    }

    @Test
    fun addWithoutTimeNeedsAnAddVerb() {
        assertEquals(Command.AddTask("Invoice for mehta"), parse("add invoice for mehta"))
        assertEquals(Command.AddTask("Thumbnail"), parse("thumbnail rakh do"))
        assertNull(parse("thumbnail"))
    }

    @Test
    fun doneAndMove() {
        assertEquals(Command.CompleteTask(TargetRef.ByName("invoice")), parse("invoice ho gaya"))
        assertEquals(Command.CompleteTask(TargetRef.ByName("invoice")), parse("done invoice"))
        assertEquals(Command.MoveBlock(TargetRef.ByName("gym"), toStartMinute = h(19)), parse("move gym to 7pm"))
        assertEquals(Command.MoveBlock(TargetRef.ByName("gym"), toDate = monday.plusDays(1), toStartMinute = h(7)), parse("gym ko kal subah 7 baje shift kar"))
    }

    @Test
    fun nonsenseIsNotGuessed() {
        assertNull(parse(""))
        assertNull(parse("hmm"))
        assertNull(parse("at 6pm"))
    }
}
