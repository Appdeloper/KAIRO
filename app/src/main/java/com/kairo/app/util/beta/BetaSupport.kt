package com.kairo.app.util.beta

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.kairo.app.BuildConfig
import com.kairo.app.R

/**
 * Beta plumbing in one place: crash log, event log, "what's new" bookkeeping and the share intents.
 * Files live in device-protected storage: they hold no personal data, and this way an alarm that rings
 * before the first unlock after a reboot can still log, and a crash there can still be saved.
 */
object BetaSupport {
    private const val PREFS = "kairo_beta"
    private const val KEY_WHATS_NEW_SEEN = "whats_new_seen"

    lateinit var crashLog: CrashLog
        private set

    fun init(context: Context) {
        val dir = context.createDeviceProtectedStorageContext().filesDir
        crashLog = CrashLog(java.io.File(dir, "last_crash.txt"))
        crashLog.install { deviceInfo() }
        Events.init(EventLog(java.io.File(dir, "beta_events.log")))
    }

    fun deviceInfo() = DeviceInfo(
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        commit = BuildConfig.GIT_SHA,
        androidRelease = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
    )

    fun versionLabel() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_SHA}"

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldShowWhatsNew(context: Context, onboardingDone: Boolean) =
        Changelog.shouldShow(BuildConfig.VERSION_NAME, prefs(context).getString(KEY_WHATS_NEW_SEEN, null), onboardingDone)

    fun markWhatsNewSeen(context: Context) = prefs(context).edit { putString(KEY_WHATS_NEW_SEEN, BuildConfig.VERSION_NAME) }

    fun releases(context: Context): List<Release> =
        runCatching { context.resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() } }
            .map(Changelog::parse).getOrDefault(emptyList())

    fun feedbackIntent(context: Context): Intent {
        val body = FeedbackReport.build(context.getString(R.string.feedback_prompt), deviceInfo(), Events.recent())
        return shareText(context.getString(R.string.feedback_subject, BuildConfig.VERSION_NAME), body, context.getString(R.string.feedback_share_title))
    }

    fun crashIntent(context: Context, report: String): Intent =
        shareText(context.getString(R.string.crash_subject, BuildConfig.VERSION_NAME), report, context.getString(R.string.crash_share_title))

    private fun shareText(subject: String, body: String, title: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body),
        title,
    )
}

/** The text of a feedback message. Pure, so the "no personal data" promise is unit-tested. */
object FeedbackReport {
    fun build(prompt: String, info: DeviceInfo, events: List<String>): String = buildString {
        appendLine(prompt)
        appendLine()
        appendLine()
        appendLine("---")
        info.lines().forEach(::appendLine)
        appendLine()
        appendLine("Last ${events.size} events (UTC, no personal data):")
        events.filter(EventLog::isValid).forEach { line ->
            val (seconds, name) = line.split(' ')
            appendLine("${java.time.Instant.ofEpochSecond(seconds.toLong())} $name")
        }
    }.trimEnd()
}
