package com.kairo.app.domain.brief

data class BriefKey(val epochDay: Long, val planHash: String)

data class CachedBrief(val key: BriefKey, val brief: Brief)

sealed interface BriefDecision {
    data class UseCached(val brief: Brief) : BriefDecision
    data object FetchCloud : BriefDecision
    data object LocalOnly : BriefDecision
}

object BriefCachePolicy {
    /**
     * Same day and same plan: reuse instantly. Anything changed: at most one cloud fetch per plan
     * version ([lastAttempt]), so reopening the screen ten times costs one quota unit, not ten.
     */
    fun decide(key: BriefKey, cached: CachedBrief?, lastAttempt: BriefKey?, aiReady: Boolean): BriefDecision = when {
        !aiReady -> BriefDecision.LocalOnly
        cached?.key == key -> BriefDecision.UseCached(cached.brief)
        lastAttempt == key -> BriefDecision.LocalOnly
        else -> BriefDecision.FetchCloud
    }
}
