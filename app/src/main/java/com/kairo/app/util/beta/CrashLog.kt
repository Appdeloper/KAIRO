package com.kairo.app.util.beta

import java.io.File

/** Version, OS and device: the same header on crash reports and feedback. */
data class DeviceInfo(val appVersion: String, val commit: String, val androidRelease: String, val sdk: Int, val device: String) {
    fun lines() = listOf(
        "KAIRO $appVersion · $commit",
        "Android $androidRelease (API $sdk)",
        "Device: $device",
    )
}

/**
 * Keeps the last crash on this phone so the next launch can offer to share it. The report holds
 * exception class names and stack frames only. Exception messages are left out on purpose: they can
 * contain task titles or other things the user typed.
 */
class CrashLog(private val file: File) {

    fun save(report: String) { runCatching { file.writeText(report) } }

    fun pending(): String? = if (file.exists()) runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null

    fun clear() { file.delete() }

    /** Saves the crash, then hands it to the previous handler so Android still shows its usual dialog. */
    fun install(info: () -> DeviceInfo) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(format(error, thread.name, info(), System.currentTimeMillis())) }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val MAX_FRAMES = 40
        private const val MAX_CAUSES = 5

        fun format(error: Throwable, threadName: String, info: DeviceInfo, atMillis: Long): String = buildString {
            info.lines().forEach(::appendLine)
            appendLine("Time: ${java.time.Instant.ofEpochMilli(atMillis)}")
            appendLine("Thread: ${threadName.take(40)}")
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSES) {
                appendLine((if (depth == 0) "" else "Caused by: ") + current.javaClass.name)
                current.stackTrace.take(MAX_FRAMES).forEach { appendLine("  at $it") }
                current = current.cause?.takeIf { it !== current }
                depth++
            }
        }.trimEnd()
    }
}
