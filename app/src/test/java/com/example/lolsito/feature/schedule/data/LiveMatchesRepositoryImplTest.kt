package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LiveMatchesRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: LiveMatchesRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = LiveMatchesRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `the live fixture maps to one in-progress match event`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLive_live.json")).build())

        val event = repository.getLiveMatches().single()

        assertEquals("/getLive", server.takeRequest().url.encodedPath)
        assertEquals(MatchState.IN_PROGRESS, event.state)
        assertEquals(Instant.parse("2026-11-11T09:00:00Z"), event.startTime)
        assertEquals("Regional Qualifier", event.blockName)
        assertEquals("LCP P&R", event.leagueName)
        assertEquals("lcp_promotion", event.leagueSlug)
        assertEquals("117393366911740182", event.leagueId)
        val match = event.match!!
        assertEquals("117393592614464348", match.id)
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 5), match.format)
        assertEquals(listOf("FAK", "GZ"), match.teams.map { it.code })
        assertEquals(listOf(2, 2), match.teams.map { it.gameWins })
    }

    @Test
    fun `the empty fixture maps to no live matches`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLive.json")).build())

        assertEquals(emptyList<Any>(), repository.getLiveMatches())
    }

    @Test
    fun `events that are not matches are ignored`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {"data":{"schedule":{"events":[
                  {"startTime":"2026-11-11T09:00:00Z","state":"inProgress","type":"show",
                   "blockName":"Pre-show","league":{"id":"1","name":"Worlds","slug":"worlds"},
                   "match":{"id":"show1","teams":[]}},
                  {"startTime":"2026-11-11T10:00:00Z","state":"inProgress","type":"match",
                   "league":{"id":"1","name":"Worlds","slug":"worlds"},
                   "match":{"id":"m1","teams":[],"strategy":{"type":"bestOf","count":3}}},
                  {"startTime":"2026-11-11T11:00:00Z","state":"inProgress","type":"match",
                   "league":{"id":"1","name":"Worlds","slug":"worlds"}}
                ]}}}
                """.trimIndent()
            ).build()
        )

        val events = repository.getLiveMatches()

        assertEquals(listOf("m1"), events.map { it.matchId })
        assertNotNull(events.single().match)
    }
}
