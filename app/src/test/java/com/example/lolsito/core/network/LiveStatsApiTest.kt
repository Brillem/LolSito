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
    fun `details sends game id and starting time without api key or locale`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("livestats_details.json")).build())

        val response = api.getDetails(gameId = "115660588583040711", startingTime = "2026-10-15T23:30:00Z")

        val request = server.takeRequest()
        assertEquals("/livestats/v1/details/115660588583040711", request.url.encodedPath)
        assertEquals("2026-10-15T23:30:00Z", request.url.queryParameter("startingTime"))
        assertNull(request.headers["x-api-key"])
        assertNull(request.url.queryParameter("hl"))
        val frames = response.body()!!.frames!!
        assertEquals(2, frames.size)
        val first = frames.last().participants!!.first()
        assertEquals(1, first.participantId)
        assertEquals(listOf(1055, 1083, 3047, 3363, 3073, 1028, 2055), first.items)
        assertEquals(8000, first.perkMetadata?.styleId)
        assertEquals(8300, first.perkMetadata?.subStyleId)
        assertEquals(8010, first.perkMetadata?.perks?.first())
        assertEquals(5949, first.totalGoldEarned)
        assertEquals(8, first.wardsPlaced)
        assertEquals(0, first.wardsDestroyed)
        assertEquals(0.29073191636216766, first.championDamageShare!!, 1e-9)
        assertEquals(0.0, first.killParticipation!!, 1e-9)
    }

    @Test
    fun `details without frames answers 204 with no body`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val response = api.getDetails(gameId = "1", startingTime = "2026-10-15T23:30:00Z")

        assertTrue(response.isSuccessful)
        assertNull(response.body())
    }

    @Test
    fun `window metadata carries the patch version`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())

        val response = api.getWindow(gameId = "115660588583040711", startingTime = "2026-10-15T23:30:00Z")

        assertEquals("16.20.826.3891", response.body()!!.gameMetadata?.patchVersion)
    }

    @Test
    fun `default base url is the livestats feed`() {
        assertEquals("https://feed.lolesports.com/livestats/v1/", ApiConfig.LIVE_STATS_BASE_URL)
    }
}
