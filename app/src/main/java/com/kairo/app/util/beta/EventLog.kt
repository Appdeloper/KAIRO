package com.kairo.app.util.beta

import java.io.File
import java.util.concurrent.Executors

/**
 * What happened recently, for beta feedback. Only fixed event names from [BetaEvent] and a timestamp
 * are stored: never task titles, names, transcripts or anything the user typed or said.
 */
enum class BetaEvent {
    APP_OPEN, ONBOARDING_DONE,
    TAB_TODAY, TAB_PLAN, TAB_ALARMS, TAB_SETTINGS,
    BRIEFING_OPEN, VOICE_START, COMMAND_SENT,
    PLAN_APPLIED, PLAN_CANCELLED, PLAN_UNDONE,
    FOCUS_SHEET_OPEN, FOCUS_SCREEN_OPEN,
    ALARM_RING_SHOWN, ALARM_DISMISSED, ALARM_SNOOZED,
    SAMPLE_LOADED, DATA_EXPORTED, DATA_RESET,
    WHATS_NEW_SHOWN, FEEDBACK_SHARED, CRASH_SHARED,
}

/** A ring buffer of the last [CAPACITY] events in a small file. Pure parts are testable without Android. */
class EventLog(private val file: File) {
    private val lock = Any()

    fun record(event: BetaEvent, atMillis: Long = System.currentTimeMillis()) = synchronized(lock) {
        val lines = read() + format(event, atMillis)
        runCatching { file.writeText(lines.takeLast(CAPACITY).joinToString("\n")) }
    }

    /** Oldest first. Unknown or damaged lines are dropped rather than shown. */
    fun read(): List<String> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching { file.readLines() }.getOrDefault(emptyList()).filter(::isValid).takeLast(CAPACITY)
    }

    fun clear() = synchronized(lock) { file.delete() }

    companion object {
        const val CAPACITY = 50

        fun format(event: BetaEvent, atMillis: Long) = "${atMillis / 1000} ${event.name}"

        /** Only "<seconds> <KNOWN_EVENT>" survives, so nothing free-form can ever leak into a report. */
        fun isValid(line: String): Boolean {
            val parts = line.split(' ')
            return parts.size == 2 && parts[0].toLongOrNull() != null && BetaEvent.entries.any { it.name == parts[1] }
        }
    }
}

/** App-wide access; a no-op until [init] runs, so tests and previews never touch files. */
object Events {
    @Volatile private var log: EventLog? = null
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kairo-events").apply { isDaemon = true } }

    fun init(log: EventLog) { this.log = log }

    fun record(event: BetaEvent) {
        val target = log ?: return
        io.execute { target.record(event) }
    }

    fun recent(): List<String> = log?.read().orEmpty()

    fun clear() { log?.clear() }
}
