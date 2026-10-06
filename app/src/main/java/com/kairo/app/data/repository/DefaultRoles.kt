package com.kairo.app.data.repository

import com.kairo.app.data.local.Role

/**
 * Seeded once on first launch. Budgets assume a heavy college day plus a part-time internship;
 * the user can tune them later. Colours are the brand lane colours (ui/design/Lanes.kt). Names are stored data, not UI strings, so they live here.
 */
object DefaultRoles {
    val all = listOf(
        Role(name = "College", colorHex = "#38D9F5", dailyBudgetMinutes = 360),
        Role(name = "Intern", colorHex = "#9B8CFF", dailyBudgetMinutes = 240),
        Role(name = "Client", colorHex = "#3EE8A8", dailyBudgetMinutes = 120),
        Role(name = "Content", colorHex = "#FF6FB7", dailyBudgetMinutes = 60),
    )
}
