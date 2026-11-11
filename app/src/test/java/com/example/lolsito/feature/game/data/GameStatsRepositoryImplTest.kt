package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GameStatsRepositoryImplTest {

    private val server = MockWebServer()
    private val clock = Clock.fixed(Instant.parse("2026-11-11T16:11:15.900Z"), ZoneOffset.UTC)
    private lateinit var repository: GameStatsRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createLiveStatsApi(server.url("/livestats/v1/").toString(), OkHttpClient())
        repository = GameStatsRepositoryImpl(api, clock)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getGameStats requests the latest aligned window and maps it`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())

        val stats = repository.getGameStats("115660588583040711")!!

        val request = server.takeRequest()
        assertEquals("/livestats/v1/window/115660588583040711", request.url.encodedPath)
        assertEquals("2026-11-11T16:10:10Z", request.url.queryParameter("startingTime"))
        assertNull(request.headers["x-api-key"])
        assertEquals(31850, stats.blueTeam.totalGold)
        assertEquals(listOf("Jax", "Trundle", "Galio", "Yunara", "Lulu"), stats.redTeam.players.map { it.championId })
    }

    @Test
    fun `getGameStats returns null when the feed has no frames`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertNull(repository.getGameStats("116299649500204434"))
    }

    @Test
    fun `getGameStats returns null when the body has no frames`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"esportsGameId":"1","frames":[]}""").build())

        assertNull(repository.getGameStats("1"))
    }

    @Test
    fun `getGameStats throws on an http error`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"httpStatus":404,"errorCode":"RESOURCE_NOT_FOUND"}""")
                .build()
        )

        try {
            repository.getGameStats("1")
            fail("Expected an exception")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }
}
