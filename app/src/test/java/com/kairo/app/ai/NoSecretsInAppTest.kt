package com.kairo.app.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CLAUDE.md rule 5: the app never holds a model API key or talks to the model provider directly.
 * Scans everything that ships or configures the build (not tests, which mention these strings here).
 */
class NoSecretsInAppTest {
    private val forbidden = listOf(
        Regex("""anthropic\.com""", RegexOption.IGNORE_CASE),
        Regex("""sk-ant-""", RegexOption.IGNORE_CASE),
        Regex("""x-api-key""", RegexOption.IGNORE_CASE),
        Regex("""ANTHROPIC_API_KEY"""),
        Regex("""anthropic-version""", RegexOption.IGNORE_CASE),
    )

    @Test
    fun appSourcesAndBuildFilesContainNoKeyOrProviderUrl() {
        // Unit tests run with the module directory (app/) as the working directory.
        val roots = listOf(File("src/main"), File("build.gradle.kts"), File("proguard-rules.pro"), File("../gradle"), File("../build.gradle.kts"), File("../gradle.properties"), File("../settings.gradle.kts"))
        assertTrue("expected to run from the app module", File("src/main/AndroidManifest.xml").exists())
        val offenders = roots.filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "toml", "properties", "pro", "json") }.toList() }
            .flatMap { file -> forbidden.filter { it.containsMatchIn(file.readText()) }.map { "${file.path}: ${it.pattern}" } }
        assertTrue("Found provider secrets/URLs in the app: $offenders", offenders.isEmpty())
    }
}
