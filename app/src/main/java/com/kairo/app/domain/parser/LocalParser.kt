package com.kairo.app.domain.parser

import com.kairo.app.domain.plan.Command
import com.kairo.app.domain.plan.MINUTES_PER_DAY
import com.kairo.app.domain.plan.TargetRef
import java.time.LocalDate

data class ParseContext(val today: LocalDate, val nowMinute: Int, val wakeMinute: Int, val sleepMinute: Int) {
    val dayEndMinute: Int get() = if (sleepMinute > wakeMinute) sleepMinute else MINUTES_PER_DAY
}

/**
 * Offline understanding of the most common things people say, in English and Hinglish. It only
 * produces a [Command]; it never decides when things happen (that's the Scheduler's job).
 * Returns null when unsure, so the caller can say "didn't get that" instead of guessing.
 */
object LocalParser {
    private val NEXT = Regex("""^(?:whats next|what is next|what next|next|next kya hai|ab kya hai|aage kya hai|ab kya h|next kya h)$""")
    private val DAY_QUERY = Regex(
        """^(?:kya hai|kya h|kya plan hai|ka plan|ka plan kya hai|ka schedule|plan|schedule|whats on|what is on|whats the plan|what do i have|my day|show my day)$""",
    )
    private val SKIP_PREFIX = Regex("""^(?:skip|cancel)\s+(.+)$""")
    private val SKIP_SUFFIX = Regex("""^(.+?)\s+(?:skip|cancel)(?:\s+(?:kar do|kar de|kardo|karde|karo|karna|kar|krdo|kr))?$""")
    private val DONE_PREFIX = Regex("""^(?:done|finished|completed|complete|mark)\s+(.+?)(?:\s+(?:as\s+)?done)?$""")
    private val DONE_SUFFIX = Regex("""^(.+?)\s+(?:ho gaya|ho gayi|ho gya|ho gyi|hogaya|hogya|kar liya|kar li|khatam|done|completed|finished)$""")
    private val MOVE_EN = Regex("""^(?:move|shift|reschedule)\s+(.+?)\s+to\s+(.+)$""")
    private val MOVE_HI = Regex("""^(.+?)\s+ko\s+(.+?)\s+(?:shift|move)(?:\s+(?:kar do|kardo|karo|kar))?$""")
    private val ADD_PREFIX = Regex("""^(?:add|schedule|create|new task|remind me to)\s+(.+)$""")
    private val ADD_VERB_SUFFIX = Regex("""\b(?:rakh do|rakhdo|rakho|rakhna|rakh|daal do|daalo|daal|add kar do|add karo|add kar|laga do|lagao)$""")

    private val DATE_WORDS = Regex("""\b(day after tomorrow|aaj|today|kal|tomorrow|tmrw|tmr|parso|parson)\b""")

    private val DURATIONS: List<Pair<Regex, (MatchResult) -> Int?>> = listOf(
        Regex("""\b(?:for\s+)?(?:half an hour|half hour|aadha ghanta|adha ghanta)\b""") to { _ -> 30 },
        Regex("""\b(?:for\s+)?(?:dedh|derh)\s+ghant[ae]\b""") to { _ -> 90 },
        Regex("""\b(?:for\s+)?(?:dhai|dhaai)\s+ghant[ae]\b""") to { _ -> 150 },
        Regex("""\b(?:for\s+)?(?:an|one|ek)\s+(?:hour|ghanta)\b""") to { _ -> 60 },
        Regex("""\b(?:for\s+)?(\d+)\s*(?:min|mins|minute|minutes|minat|mnt)\b""") to { m -> m.groupValues[1].toIntOrNull() },
        Regex("""\b(?:for\s+)?(\d+(?:\.\d+)?)\s*(?:h|hr|hrs|hour|hours|ghanta|ghante)\b""") to { m ->
            m.groupValues[1].toDoubleOrNull()?.let { (it * 60).toInt() }
        },
    )

    /** Verbs and glue words that carry no title meaning once time/date are cut out. */
    private val FILLERS = setOf(
        "at", "@", "rakh", "rakho", "rakhna", "rakhdo", "daal", "daalo", "add", "set", "schedule",
        "kar", "karo", "karna", "kardo", "laga", "lagao", "please", "pls", "mujhe", "baje",
    )

    /** Allowed in a title's middle ("client ka call") but not at its edges ("ka gym"). */
    private val EDGE_FILLERS = setOf("ka", "ki", "ke", "ko", "the", "my", "mera", "meri", "wala", "wali", "do", "to", "pe", "par", "for", "on")

    fun parse(input: String, context: ParseContext): Command? {
        val text = normalize(input)
        if (text.isEmpty()) return null
        return parseQuery(text, context)
            ?: parseSkip(text, context)
            ?: parseDone(text)
            ?: parseMove(text, context)
            ?: parseAdd(text, context)
    }

    fun normalize(input: String): String = input.lowercase()
        .replace("a.m.", "am").replace("p.m.", "pm")
        .replace(Regex("""['’`]"""), "")
        .replace(Regex("""(?<!\d)\.|\.(?!\d)"""), " ")
        .replace(Regex("""[?!,;"]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun parseQuery(text: String, context: ParseContext): Command? {
        if (NEXT.matches(text)) return Command.QueryDay(nextOnly = true)
        val (date, rest) = extractDate(text, context)
        return if (DAY_QUERY.matches(rest)) Command.QueryDay(date = date) else null
    }

    private fun parseSkip(text: String, context: ParseContext): Command? {
        val raw = (SKIP_PREFIX.find(text) ?: SKIP_SUFFIX.find(text))?.groupValues?.get(1) ?: return null
        val (date, rest) = extractDate(raw, context)
        val target = cleanTitle(rest).ifEmpty { return null }
        return Command.SkipBlock(TargetRef.ByName(target), date)
    }

    private fun parseDone(text: String): Command? {
        val raw = (DONE_PREFIX.find(text) ?: DONE_SUFFIX.find(text))?.groupValues?.get(1) ?: return null
        val target = cleanTitle(raw).ifEmpty { return null }
        return Command.CompleteTask(TargetRef.ByName(target))
    }

    private fun parseMove(text: String, context: ParseContext): Command? {
        val m = MOVE_EN.find(text) ?: MOVE_HI.find(text) ?: return null
        val target = cleanTitle(m.groupValues[1]).ifEmpty { return null }
        val (date, afterDate) = extractDate(m.groupValues[2], context)
        val (hint, afterHint) = extractHint(afterDate)
        val time = HinglishTimeNormalizer.find(afterHint)
        if (time == null && date == null) return null
        val start = time?.let { resolve(it.clock, hint, date ?: context.today, context) }
        return Command.MoveBlock(TargetRef.ByName(target), date, start)
    }

    private fun parseAdd(text: String, context: ParseContext): Command? {
        val prefixed = ADD_PREFIX.find(text)
        var body = prefixed?.groupValues?.get(1) ?: text
        val hasAddVerb = ADD_VERB_SUFFIX.containsMatchIn(body)

        var duration: Int? = null
        for ((regex, value) in DURATIONS) {
            val m = regex.find(body) ?: continue
            duration = value(m)
            body = body.removeRange(m.range)
            break
        }
        val (date, afterDate) = extractDate(body, context)
        val (hint, afterHint) = extractHint(afterDate)
        val time = HinglishTimeNormalizer.find(afterHint)
        // A bare phrase with no time and no "add"/"rakh" is too vague to act on.
        if (prefixed == null && time == null && !hasAddVerb) return null

        val title = cleanTitle(time?.let { afterHint.removeRange(it.range) } ?: afterHint).ifEmpty { return null }
        val start = time?.let { resolve(it.clock, hint, date ?: context.today, context) }
        return Command.AddTask(title = title.replaceFirstChar { it.uppercase() }, date = date, startMinute = start, durationMinutes = duration)
    }

    private fun resolve(clock: ClockTime, hint: DayHint?, date: LocalDate, context: ParseContext): Int =
        HinglishTimeNormalizer.toMinuteOfDay(
            clock, hint,
            isToday = date == context.today,
            nowMinute = context.nowMinute,
            wakeMinute = context.wakeMinute,
            dayEndMinute = context.dayEndMinute,
        )

    /** "kal" means tomorrow here: in a planner people talk about the future far more than the past. */
    private fun extractDate(text: String, context: ParseContext): Pair<LocalDate?, String> {
        val m = DATE_WORDS.find(text) ?: return null to text.trim()
        val offset = when (m.groupValues[1]) {
            "aaj", "today" -> 0L
            "parso", "parson", "day after tomorrow" -> 2L
            else -> 1L
        }
        return context.today.plusDays(offset) to collapse(text.removeRange(m.range))
    }

    private fun extractHint(text: String): Pair<DayHint?, String> {
        val (hint, range) = HinglishTimeNormalizer.findHint(text) ?: return null to text
        return hint to collapse(text.removeRange(range))
    }

    private fun cleanTitle(text: String): String {
        val words = collapse(text).split(" ").filter { it.isNotEmpty() && it !in FILLERS }.toMutableList()
        while (words.isNotEmpty() && words.first() in EDGE_FILLERS) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in EDGE_FILLERS) words.removeAt(words.lastIndex)
        return words.joinToString(" ")
    }

    private fun collapse(s: String) = s.replace(Regex("""\s+"""), " ").trim()
}
