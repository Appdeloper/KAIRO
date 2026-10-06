package com.kairo.app.util.beta

/** One release in the "What's new" sheet. */
data class Release(val version: String, val items: List<String>)

/**
 * Parses res/raw/changelog.txt: "## <version>" starts a release, "- <text>" is an item, anything
 * else is ignored. Newest release first, as written in the file.
 */
object Changelog {
    fun parse(text: String): List<Release> {
        val releases = mutableListOf<Release>()
        var version: String? = null
        val items = mutableListOf<String>()
        fun flush() {
            version?.let { if (items.isNotEmpty()) releases += Release(it, items.toList()) }
            items.clear()
        }
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("## ") -> { flush(); version = line.removePrefix("## ").trim() }
                line.startsWith("- ") && version != null -> items += line.removePrefix("- ").trim()
            }
        }
        flush()
        return releases
    }

    /** Show the sheet once per version, and never on the very first launch (onboarding covers that). */
    fun shouldShow(currentVersion: String, lastSeenVersion: String?, onboardingDone: Boolean): Boolean =
        onboardingDone && lastSeenVersion != currentVersion
}
