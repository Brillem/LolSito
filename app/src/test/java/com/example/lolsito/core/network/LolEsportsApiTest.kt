package com.example.lolsito.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import com.example.lolsito.testutil.Fixtures
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LolEsportsApiTest {

    private val server = MockWebServer()
    private lateinit var api: LolEsportsApi

    @Before
    fun setUp() {
        server.start()
        api = NetworkModule.createApi(
            baseUrl = server.url("/persisted/gw/").toString(),
            client = NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getSchedule sends api key, locale and filters`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getSchedule_worlds.json")).build())

        val response = api.getSchedule(leagueIds = "1,2", pageToken = "tok")

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getSchedule", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("1,2", request.url.queryParameter("leagueId"))
        assertEquals("tok", request.url.queryParameter("pageToken"))
        assertEquals(80, response.data.schedule.events.size)
    }

    @Test
    fun `getLeagues sends api key and parses`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLeagues.json")).build())

        val response = api.getLeagues()

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getLeagues", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals(51, response.data.leagues.size)
    }

    @Test
    fun `getEventDetails sends api key, locale and id`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getEventDetails.json")).build())

        val response = api.getEventDetails(id = "115660588583040710")

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getEventDetails", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("115660588583040710", request.url.queryParameter("id"))
        assertEquals(5, response.data.event?.match?.games?.size)
    }

    @Test
    fun `getTournamentsForLeague sends api key, locale and league id`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getTournamentsForLeague_worlds.json")).build())

        val response = api.getTournamentsForLeague(leagueId = "98767975604431411")

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getTournamentsForLeague", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("98767975604431411", request.url.queryParameter("leagueId"))
        assertEquals(11, response.data.leagues.single().tournaments.size)
    }

    @Test
    fun `getStandings sends api key, locale and tournament id`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getStandings_worlds2026.json")).build())

        val response = api.getStandings(tournamentId = "115660540725177488")

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getStandings", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("115660540725177488", request.url.queryParameter("tournamentId"))
        assertEquals(3, response.data.standings.single().stages.size)
    }

    @Test
    fun `getLive sends api key and locale and parses the live fixture`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLive_live.json")).build())

        val response = api.getLive()

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("/persisted/gw/getLive", request.url.encodedPath)
        assertEquals("en-US", request.url.queryParameter("hl"))
        val event = response.data.schedule.events.single()
        assertEquals("match", event.type)
        assertEquals("inProgress", event.state)
        assertEquals("117393366911740182", event.league?.id)
        assertEquals("117393592614464348", event.match?.id)
    }

    @Test
    fun `getLive parses the empty fixture as no events`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLive.json")).build())

        assertEquals(emptyList<Any>(), api.getLive().data.schedule.events)
    }
}
