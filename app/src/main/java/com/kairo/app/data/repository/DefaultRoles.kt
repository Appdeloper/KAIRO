package com.kairo.app.data.repository

import com.kairo.app.data.local.Role

/**
 * Seeded once on first launch. Budgets assume a heavy college day plus a part-time internship;
 * the user can tune them later. Names are stored data, not UI strings, so they live here.
 */
object DefaultRoles {
    val all = listOf(
        Role(name = "College", colorHex = "#00E5FF", dailyBudgetMinutes = 360),
        Role(name = "Intern", colorHex = "#B388FF", dailyBudgetMinutes = 240),
        Role(name = "Client", colorHex = "#39FF88", dailyBudgetMinutes = 120),
        Role(name = "Content", colorHex = "#FF2E93", dailyBudgetMinutes = 60),
    )
}
