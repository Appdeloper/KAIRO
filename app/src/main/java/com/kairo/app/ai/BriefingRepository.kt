package com.kairo.app.ai

import com.kairo.app.data.prefs.BriefCacheStore
import com.kairo.app.domain.Greeting
import com.kairo.app.domain.brief.Brief
import com.kairo.app.domain.brief.BriefCachePolicy
import com.kairo.app.domain.brief.BriefDecision
import com.kairo.app.domain.brief.BriefKey
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.domain.brief.BriefStrings
import com.kairo.app.domain.brief.LocalBriefBuilder
import com.kairo.app.domain.brief.PlanHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** [isFinal] = false means a better (cloud) brief may still replace this one. */
data class BriefUpdate(val brief: Brief, val source: BriefSource, val isFinal: Boolean)

/**
 * Decides cache vs cloud vs local and emits what to show. The first emission is always immediate
 * (cache or local template), so the screen is never empty while the network is slow.
 */
class BriefingRepository(
    private val client: BriefClient,
    private val cache: BriefCacheStore,
    private val settings: suspend () -> AiSettings,
    private val snapshot: suspend () -> PlannerSnapshot,
) {
    fun briefs(strings: BriefStrings): Flow<BriefUpdate> = flow {
        val snap = snapshot()
        val state = snap.state
        val key = BriefKey(state.today.toEpochDay(), PlanHasher.hash(state, state.today, Greeting.dayPartFor(state.nowMinute)))
        val local = LocalBriefBuilder.build(state, snap.firstName, strings)

        when (val decision = BriefCachePolicy.decide(key, cache.cached(), cache.lastAttempt(), settings().isReady)) {
            is BriefDecision.UseCached -> emit(BriefUpdate(decision.brief, BriefSource.CACHE, isFinal = true))
            BriefDecision.LocalOnly -> emit(BriefUpdate(local, BriefSource.LOCAL, isFinal = true))
            BriefDecision.FetchCloud -> {
                emit(BriefUpdate(local, BriefSource.LOCAL, isFinal = false))
                // Recorded before the call so a crash mid-request still counts as this plan's one try.
                cache.recordAttempt(key)
                when (val result = client.fetch(snap, LocalBriefBuilder.freeSlots(state))) {
                    is BriefFetch.Success -> {
                        cache.save(key, result.brief)
                        emit(BriefUpdate(result.brief, BriefSource.CLOUD, isFinal = true))
                    }
                    is BriefFetch.Failed -> {
                        // Unreachable network never hit the Worker or the quota: allow a retry next open.
                        if (result.reason == FailReason.Network) cache.clearAttempt()
                        emit(BriefUpdate(local, BriefSource.LOCAL, isFinal = true))
                    }
                }
            }
        }
    }
}
