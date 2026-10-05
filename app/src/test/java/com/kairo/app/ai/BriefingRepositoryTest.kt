package com.kairo.app.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kairo.app.data.prefs.BriefCacheStore
import com.kairo.app.domain.brief.BriefSource
import com.kairo.app.domain.brief.FakeBriefStrings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.UnknownHostException

class BriefingRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cloudJson = """{"greeting":"Hey Aarav","summary":"Cloud summary.","bestGap":null,"ifThenPlans":[]}"""
    private val cache by lazy { BriefCacheStore(PreferenceDataStoreFactory.create { tmp.newFile("brief.preferences_pb") }) }

    private fun repo(http: FakeHttp, settings: AiSettings = READY) = BriefingRepository(
        client = BriefClient(http.client, { settings }),
        cache = cache,
        settings = { settings },
        snapshot = { PlannerSnapshot(aiState(), "Aarav") },
    )

    private suspend fun open(r: BriefingRepository) = r.briefs(FakeBriefStrings).toList()

    @Test
    fun firstOpenShowsLocalThenCloud_secondOpenUsesCacheWithoutNetwork() = runBlocking {
        val http = FakeHttp { respond(it, 200, cloudJson) }
        val first = open(repo(http))
        assertEquals(listOf(BriefSource.LOCAL to false, BriefSource.CLOUD to true), first.map { it.source to it.isFinal })
        assertEquals("Hey Aarav", first.last().brief.greeting)

        val second = open(repo(http))
        assertEquals(listOf(BriefSource.CACHE to true), second.map { it.source to it.isFinal })
        assertEquals(1, http.requests.size)
    }

    @Test
    fun serverErrorFallsBackToLocalAndDoesNotRetryForTheSamePlan() = runBlocking {
        val http = FakeHttp { respond(it, 502, "{}") }
        assertEquals(BriefSource.LOCAL to true, open(repo(http)).last().let { it.source to it.isFinal })
        assertEquals(BriefSource.LOCAL to true, open(repo(http)).single().let { it.source to it.isFinal })
        assertEquals("max one cloud fetch per plan version", 1, http.requests.size)
    }

    @Test
    fun offlineFallsBackToLocalAndRetriesNextOpen() = runBlocking {
        val offline = FakeHttp { throw UnknownHostException("airplane mode") }
        val shown = open(repo(offline)).last()
        assertEquals(BriefSource.LOCAL, shown.source)
        assertEquals("MORNING:Aarav", shown.brief.greeting)
        open(repo(offline))
        assertEquals("unreachable network doesn't burn the attempt", 2, offline.requests.size)
    }

    @Test
    fun aiOffIsLocalOnly() = runBlocking {
        val http = FakeHttp { error("AI off must not use the network") }
        val updates = open(repo(http, READY.copy(enabled = false)))
        assertEquals(listOf(BriefSource.LOCAL to true), updates.map { it.source to it.isFinal })
    }
}
