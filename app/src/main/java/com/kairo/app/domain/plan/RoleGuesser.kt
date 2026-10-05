package com.kairo.app.domain.plan

import com.kairo.app.data.local.Role

/**
 * Cheap keyword guess for which role a spoken task belongs to. A wrong guess is visible in the
 * PlanDiff preview, so this only needs to be right most of the time.
 */
object RoleGuesser {
    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "college" to listOf("lecture", "class", "assignment", "exam", "lab", "study", "padhai", "college", "viva", "quiz", "homework", "notes", "revision"),
        "intern" to listOf("intern", "standup", "office", "report", "manager", "sprint", "jira", "internship"),
        "client" to listOf("client", "invoice", "call", "freelance", "proposal", "logo", "website", "payment", "meeting"),
        "content" to listOf("reel", "video", "post", "edit", "shoot", "script", "thumbnail", "youtube", "insta", "instagram", "content", "upload", "story"),
    )

    fun guess(title: String, roles: List<Role>): Role? {
        val words = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()
        roles.firstOrNull { it.name.lowercase() in words }?.let { return it }
        return roles.firstOrNull { role -> KEYWORDS[role.name.lowercase()].orEmpty().any { it in words } }
    }
}
