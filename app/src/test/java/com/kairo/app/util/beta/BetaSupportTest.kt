package com.kairo.app.util.beta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BetaSupportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val info = DeviceInfo("0.1.0-beta (42)", "abc1234", "16", 36, "Google Pixel 9")

    @Test fun eventLogKeepsOnlyTheLast50() {
        val log = EventLog(tmp.newFile())
        repeat(60) { log.record(BetaEvent.TAB_TODAY, atMillis = it * 1000L) }
        val lines = log.read()
        assertEquals(EventLog.CAPACITY, lines.size)
        assertEquals("10 TAB_TODAY", lines.first())
        assertEquals("59 TAB_TODAY", lines.last())
    }

    @Test fun eventLogDropsAnythingThatIsNotAKnownEvent() {
        val file = tmp.newFile()
        // A damaged or tampered file must never put free text into a feedback report.
        file.writeText("1 APP_OPEN\n2 Gym with Riya at 6pm\n3 PLAN_APPLIED extra\nnot a line\n4 PLAN_APPLIED")
        assertEquals(listOf("1 APP_OPEN", "4 PLAN_APPLIED"), EventLog(file).read())
    }

    @Test fun eventLogClearRemovesEverything() {
        val log = EventLog(tmp.newFile())
        log.record(BetaEvent.APP_OPEN)
        log.clear()
        assertTrue(log.read().isEmpty())
    }

    @Test fun crashReportLeavesOutExceptionMessages() {
        val error = IllegalStateException("task 'Call Riya +91 98765 43210' broke", RuntimeException("riya@example.com"))
        val report = CrashLog.format(error, "main", info, 0L)
        assertFalse(report.contains("Riya"))
        assertFalse(report.contains("98765"))
        assertFalse(report.contains("example.com"))
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("Caused by: java.lang.RuntimeException"))
        assertTrue(report.contains("KAIRO 0.1.0-beta (42) · abc1234"))
        assertTrue(report.contains("Device: Google Pixel 9"))
    }

    @Test fun crashLogSavesAndClears() {
        val log = CrashLog(java.io.File(tmp.root, "crash.txt"))
        assertNull(log.pending())
        log.save("report")
        assertEquals("report", log.pending())
        log.clear()
        assertNull(log.pending())
    }

    @Test fun feedbackHasDeviceInfoAndEventNamesOnly() {
        val text = FeedbackReport.build("Tell us", info, listOf("0 APP_OPEN", "60 Gym with Riya", "120 PLAN_APPLIED"))
        assertTrue(text.startsWith("Tell us"))
        assertTrue(text.contains("Android 16 (API 36)"))
        assertTrue(text.contains("1970-01-01T00:00:00Z APP_OPEN"))
        assertTrue(text.contains("1970-01-01T00:02:00Z PLAN_APPLIED"))
        assertFalse(text.contains("Riya"))
    }

    @Test fun changelogParsesReleasesNewestFirst() {
        val releases = Changelog.parse(
            """
            Intro text is ignored.
            ## 0.2.0-beta
            - Second
            - Third
            ## 0.1.0-beta
            - First
            ## 0.0.1
            """.trimIndent(),
        )
        assertEquals(listOf("0.2.0-beta", "0.1.0-beta"), releases.map { it.version })
        assertEquals(listOf("Second", "Third"), releases[0].items)
    }

    @Test fun whatsNewShowsOncePerVersionAfterOnboarding() {
        assertFalse(Changelog.shouldShow("0.1.0-beta", null, onboardingDone = false))
        assertTrue(Changelog.shouldShow("0.1.0-beta", null, onboardingDone = true))
        assertFalse(Changelog.shouldShow("0.1.0-beta", "0.1.0-beta", onboardingDone = true))
        assertTrue(Changelog.shouldShow("0.2.0-beta", "0.1.0-beta", onboardingDone = true))
    }

    @Test fun bundledChangelogParses() {
        val text = java.io.File("src/main/res/raw/changelog.txt").readText()
        val releases = Changelog.parse(text)
        assertTrue(releases.isNotEmpty())
        assertTrue(releases.first().items.isNotEmpty())
    }
}
