package com.example.lolsito.core.network

import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveStatsApiTest {

    private val server = MockWebServer()
    private lateinit var api: LiveStatsApi

    @Before
    fun setUp() {
        server.start()
        api = NetworkModule.createLiveStatsApi(
            baseUrl = server.url("/livestats/v1/").toString(),
            client = OkHttpClient(),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `window sends game id and starting time without api key or locale`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())

        val response = api.getWindow(gameId = "115660588583040711", startingTime = "2026-10-15T23:30:00Z")

        val request = server.takeRequest()
        assertEquals("/livestats/v1/window/115660588583040711", request.url.encodedPath)
        assertEquals("2026-10-15T23:30:00Z", request.url.queryParameter("startingTime"))
        assertNull(request.headers["x-api-key"])
        assertNull(request.url.queryParameter("hl"))
        val body = response.body()!!
        assertEquals("115660588583040711", body.esportsGameId)
        assertEquals(35, body.frames?.size)
        assertEquals("MVK Kratos", body.gameMetadata?.blueTeamMetadata?.participantMetadata?.first()?.summonerName)
    }

    @Test
    fun `window without frames answers 204 with no body`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val response = api.getWindow(gameId = "1", startingTime = "2026-10-15T23:30:00Z")

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
        assertNull(response.body())
    }

    @Test
    fun `default base url is the livestats feed`() {
        assertEquals("https://feed.lolesports.com/livestats/v1/", ApiConfig.LIVE_STATS_BASE_URL)
    }
}
