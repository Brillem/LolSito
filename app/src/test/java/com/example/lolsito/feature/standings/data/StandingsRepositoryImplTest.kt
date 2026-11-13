package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.network.ApiConfig
import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StandingsRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: StandingsRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/persisted/gw/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = StandingsRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getTournaments queries by league id and maps tournaments`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getTournamentsForLeague_worlds.json")).build())

        val tournaments = repository.getTournaments("98767975604431411")

        val request = server.takeRequest()
        assertEquals("/persisted/gw/getTournamentsForLeague", request.url.encodedPath)
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("98767975604431411", request.url.queryParameter("leagueId"))
        assertEquals(11, tournaments.size)
        assertEquals("worlds_2026", tournaments.first().slug)
    }

    @Test
    fun `getStandings queries by tournament id and maps stages`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getStandingsV3_lck.json")).build())

        val standings = repository.getStandings("115548147896555737")

        val request = server.takeRequest()
        assertEquals("/persisted/gw/getStandingsV3", request.url.encodedPath)
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("115548147896555737", request.url.queryParameter("tournamentId"))
        assertEquals(3, standings.stages.size)
        assertTrue(standings.stages.first().sections.first().rankings.isNotEmpty())
    }
}
