package com.kairo.app.domain.brief

/** A free window the brief recommends; always one the scheduler actually found. */
data class BestGap(val startMinute: Int, val endMinute: Int, val suggestion: String)

data class Brief(
    val greeting: String,
    val summary: String,
    val bestGap: BestGap?,
    val ifThenPlans: List<String>,
)

enum class BriefSource { CLOUD, CACHE, LOCAL }
