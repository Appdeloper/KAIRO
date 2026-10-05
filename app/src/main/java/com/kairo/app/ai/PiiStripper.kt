package com.kairo.app.ai

/**
 * Same rules as the Worker's stripPii: emails and phone-like digit runs (8+ digits) are replaced
 * before text leaves the phone (CLAUDE.md rule 8). Times like 18:30 and ISO dates survive.
 */
object PiiStripper {
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val PHONE_CANDIDATE = Regex("""\+?\(?\d[\d\s().-]{6,}\d""")
    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private const val MIN_PHONE_DIGITS = 8

    fun strip(text: String): String = text
        .replace(EMAIL, "[email]")
        .replace(PHONE_CANDIDATE) { match ->
            val trimmed = match.value.trim()
            val digits = trimmed.count { it.isDigit() }
            if (ISO_DATE.matches(trimmed) || digits < MIN_PHONE_DIGITS) match.value else "[phone]"
        }
}
