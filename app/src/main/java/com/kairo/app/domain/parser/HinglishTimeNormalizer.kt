package com.kairo.app.domain.parser

enum class Meridiem { AM, PM }

/** Spoken part-of-day words. They disambiguate "7" into 7:00 or 19:00. */
enum class DayHint { MORNING, AFTERNOON, EVENING, NIGHT }

/** A clock reading as spoken. [hour] is 0–23 when given in 24h form, otherwise 1–12. */
data class ClockTime(val hour: Int, val minute: Int, val meridiem: Meridiem? = null)

data class TimeMatch(val clock: ClockTime, val range: IntRange)

/**
 * Understands how times are actually said in Hinglish: "saade chhe" (6:30), "pone saat" (6:45),
 * "sava paanch" (5:15), "dedh" (1:30), "dhai" (2:30), plus English "6pm" / "at 6:30".
 * Expects lowercase input.
 */
object HinglishTimeNormalizer {
    val NUMBER_WORDS: Map<String, Int> = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4,
        "paanch" to 5, "panch" to 5, "paach" to 5,
        "chhe" to 6, "chhah" to 6, "chheh" to 6, "che" to 6, "chah" to 6, "chhai" to 6,
        "saat" to 7, "aath" to 8, "aat" to 8, "nau" to 9, "das" to 10, "dus" to 10,
        "gyarah" to 11, "gyara" to 11, "gyaarah" to 11, "barah" to 12, "baarah" to 12, "bara" to 12, "baara" to 12,
    )

    private val HALF_PAST = listOf("saade", "saadhe", "sade", "sadhe", "saarhe")
    private val QUARTER_PAST = listOf("sava", "savaa", "sawa", "sawaa")
    private val QUARTER_TO = listOf("paune", "pone", "poune", "pauney", "paun")
    private val ONE_THIRTY = listOf("dedh", "derh", "dhedh")
    private val TWO_THIRTY = listOf("dhai", "dhaai", "adhai", "adhaai", "dhaayi")

    val HINT_WORDS: Map<String, DayHint> = mapOf(
        "subah" to DayHint.MORNING, "subha" to DayHint.MORNING, "savere" to DayHint.MORNING,
        "sawere" to DayHint.MORNING, "morning" to DayHint.MORNING,
        "dopahar" to DayHint.AFTERNOON, "dopehar" to DayHint.AFTERNOON, "dophar" to DayHint.AFTERNOON,
        "dupahar" to DayHint.AFTERNOON, "afternoon" to DayHint.AFTERNOON,
        "shaam" to DayHint.EVENING, "sham" to DayHint.EVENING, "evening" to DayHint.EVENING,
        "raat" to DayHint.NIGHT, "rat" to DayHint.NIGHT, "night" to DayHint.NIGHT, "tonight" to DayHint.NIGHT,
    )

    private fun alt(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|")

    private val NUM = "(?:\\d{1,2}|${alt(NUMBER_WORDS.keys)})"
    private const val BAJE = "(?:baje|bje|bajey|baaje|bajay)"
    private val MODIFIER = alt(HALF_PAST + QUARTER_PAST + QUARTER_TO)

    // Ordered from least to most ambiguous; the first pattern that matches anywhere wins.
    private val PATTERNS: List<Pair<Regex, (MatchResult) -> ClockTime?>> = listOf(
        Regex("""\b(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)\b""") to { m ->
            clock(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, if (m.groupValues[3] == "am") Meridiem.AM else Meridiem.PM)
        },
        Regex("""\b($MODIFIER)\s+($NUM)(?:\s+$BAJE)?\b""") to { m -> withModifier(m.groupValues[1], m.groupValues[2]) },
        Regex("""\b(${alt(ONE_THIRTY + TWO_THIRTY)})(?:\s+$BAJE)?\b""") to { m ->
            if (m.groupValues[1] in ONE_THIRTY) ClockTime(1, 30) else ClockTime(2, 30)
        },
        Regex("""\b($NUM)(?:[:.](\d{2}))?\s*$BAJE\b""") to { m ->
            numberValue(m.groupValues[1])?.let { clock(it, m.groupValues[2].toIntOrNull() ?: 0, null) }
        },
        Regex("""(?:\bat|@)\s*(\d{1,2})(?:[:.](\d{2}))?\b""") to { m ->
            clock(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, null)
        },
        Regex("""\b(\d{1,2})[:.](\d{2})\b""") to { m -> clock(m.groupValues[1].toInt(), m.groupValues[2].toInt(), null) },
    )

    fun numberValue(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

    /** Finds the first time expression in [text], with its character range so callers can cut it out. */
    fun find(text: String): TimeMatch? {
        for ((regex, build) in PATTERNS) {
            for (m in regex.findAll(text)) {
                build(m)?.let { return TimeMatch(it, m.range) }
            }
        }
        return null
    }

    /** Parses a phrase that is only a time, e.g. "saade chhe" -> 6:30. Null if anything else is in it. */
    fun parse(phrase: String): ClockTime? {
        val text = phrase.lowercase().trim()
        val match = find(text) ?: return null
        return match.clock.takeIf { match.range.first == 0 && match.range.last == text.length - 1 }
    }

    fun findHint(text: String): Pair<DayHint, IntRange>? =
        Regex("""\b(${alt(HINT_WORDS.keys)})\b""").find(text)?.let { HINT_WORDS.getValue(it.groupValues[1]) to it.range }

    /**
     * Turns a spoken clock into minutes after midnight. Without am/pm or a hint we pick the first
     * reading that is still ahead (for today) and inside waking hours: "6 baje" at 9am means 18:00.
     */
    fun toMinuteOfDay(clock: ClockTime, hint: DayHint?, isToday: Boolean, nowMinute: Int, wakeMinute: Int, dayEndMinute: Int): Int {
        clock.meridiem?.let { return meridiemMinute(clock, it) }
        if (clock.hour == 0 || clock.hour > 12) return clock.hour * 60 + clock.minute
        hint?.let { return withHint(clock, it) }
        val am = (clock.hour % 12) * 60 + clock.minute
        val pm = am + 12 * 60
        val earliest = if (isToday) maxOf(wakeMinute, nowMinute) else wakeMinute
        return listOf(am, pm).firstOrNull { it in earliest..dayEndMinute } ?: pm
    }

    fun withHint(clock: ClockTime, hint: DayHint): Int {
        val h = clock.hour % 12
        val hour24 = when (hint) {
            DayHint.MORNING -> h
            // "dopahar 12" is noon, "dopahar 2" is 14:00, "dopahar 11" stays late morning.
            DayHint.AFTERNOON -> if (clock.hour == 12 || clock.hour <= 6) h + 12 else h
            DayHint.EVENING -> h + 12
            // "raat 10" is 22:00, but "raat 2" is 2am and "raat 12" is midnight.
            DayHint.NIGHT -> if (clock.hour in 6..11) h + 12 else h
        }
        return hour24 * 60 + clock.minute
    }

    private fun meridiemMinute(clock: ClockTime, meridiem: Meridiem): Int {
        val h = clock.hour % 12 + if (meridiem == Meridiem.PM) 12 else 0
        return h * 60 + clock.minute
    }

    private fun withModifier(modifier: String, numberToken: String): ClockTime? {
        val n = numberValue(numberToken)?.takeIf { it in 1..12 } ?: return null
        return when (modifier) {
            in HALF_PAST -> ClockTime(n, 30)
            in QUARTER_PAST -> ClockTime(n, 15)
            else -> ClockTime(if (n == 1) 12 else n - 1, 45) // paune saat = 6:45, paune ek = 12:45
        }
    }

    private fun clock(hour: Int, minute: Int, meridiem: Meridiem?): ClockTime? {
        if (minute !in 0..59) return null
        val validHour = if (meridiem != null) hour in 1..12 else hour in 0..23
        return if (validHour) ClockTime(hour, minute, meridiem) else null
    }
}
