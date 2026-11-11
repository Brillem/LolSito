package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue

class GameStatsRepositoryImplTest {

    private val server = MockWebServer()
    private val windowResponses = ConcurrentLinkedQueue<MockResponse>()
    private val detailsResponses = ConcurrentLinkedQueue<MockResponse>()
    private val clock = Clock.fixed(Instant.parse("2026-11-11T16:11:15.900Z"), ZoneOffset.UTC)
    private lateinit var repository: GameStatsRepositoryImpl

    @Before
    fun setUp() {
        // The two feeds are requested concurrently, so each has its own queue instead of one shared by arrival order.
        // A request the test did not queue a response for (the details feed) fails fast instead of hanging.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val queue = if (request.url.encodedPath.contains("/details/")) detailsResponses else windowResponses
                return queue.poll() ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val api = NetworkModule.createLiveStatsApi(server.url("/livestats/v1/").toString(), OkHttpClient())
        repository = GameStatsRepositoryImpl(api, clock)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getGameStats requests the latest aligned window and maps it`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())

        val stats = repository.getGameStats("115660588583040711")!!

        // The details feed is requested too (and fails fast here), in no defined order.
        val request = List(2) { server.takeRequest() }.single { it.url.encodedPath.contains("/window/") }
        assertEquals("/livestats/v1/window/115660588583040711", request.url.encodedPath)
        assertEquals("2026-11-11T16:10:10Z", request.url.queryParameter("startingTime"))
        assertNull(request.headers["x-api-key"])
        assertEquals(31850, stats.blueTeam.totalGold)
        assertEquals(listOf("Jax", "Trundle", "Galio", "Yunara", "Lulu"), stats.redTeam.players.map { it.championId })
    }

    @Test
    fun `getGameStats returns null when the feed has no frames`() = runBlocking {
        windowResponses.add(MockResponse.Builder().code(204).build())

        assertNull(repository.getGameStats("116299649500204434"))
    }

    @Test
    fun `getGameStats returns null when the body has no frames`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body("""{"esportsGameId":"1","frames":[]}""").build())

        assertNull(repository.getGameStats("1"))
    }

    @Test
    fun `getGameStats throws on an http error`() = runBlocking {
        windowResponses.add(
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

    @Test
    fun `window and details are requested with the same starting time and joined by participant`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())
        detailsResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_details.json")).build())

        val stats = repository.getGameStats("115660588583040711")!!

        // The feeds are requested concurrently, so their order of arrival is not defined.
        val requests = List(2) { server.takeRequest() }
        val window = requests.single { it.url.encodedPath.contains("/window/") }
        val details = requests.single { it.url.encodedPath.contains("/details/") }
        assertEquals("/livestats/v1/window/115660588583040711", window.url.encodedPath)
        assertEquals("/livestats/v1/details/115660588583040711", details.url.encodedPath)
        assertEquals("2026-11-11T16:10:10Z", window.url.queryParameter("startingTime"))
        assertEquals("2026-11-11T16:10:10Z", details.url.queryParameter("startingTime"))
        assertNull(details.headers["x-api-key"])
        assertEquals(listOf(1055, 1083, 3047, 3363, 3073, 1028, 2055), stats.blueTeam.players.first().details?.itemIds)
        assertEquals(8214, stats.redTeam.players.last().details?.keystoneId)
        assertEquals("16.20.1", stats.dataDragonVersion)
        assertEquals(31850, stats.blueTeam.totalGold)
    }

    @Test
    fun `both requests share the starting time even when the clock moves between them`() = runBlocking {
        val moving = object : Clock() {
            private var calls = 0
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant(): Instant = Instant.parse("2026-11-11T16:11:15.900Z").plusSeconds(30L * calls++)
        }
        val api = NetworkModule.createLiveStatsApi(server.url("/livestats/v1/").toString(), OkHttpClient())
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())
        detailsResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_details.json")).build())

        GameStatsRepositoryImpl(api, moving).getGameStats("1")

        assertEquals("2026-11-11T16:10:10Z", server.takeRequest().url.queryParameter("startingTime"))
        assertEquals("2026-11-11T16:10:10Z", server.takeRequest().url.queryParameter("startingTime"))
    }

    @Test
    fun `details answering 204 leaves the window stats intact`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())
        detailsResponses.add(MockResponse.Builder().code(204).build())

        val stats = repository.getGameStats("115660588583040711")!!

        assertEquals(31850, stats.blueTeam.totalGold)
        assertEquals(10, (stats.blueTeam.players + stats.redTeam.players).size)
        assertTrue((stats.blueTeam.players + stats.redTeam.players).all { it.details == null })
        assertEquals("16.20.1", stats.dataDragonVersion)
    }

    @Test
    fun `details failing with an http error or a bad body leaves the window stats intact`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())
        detailsResponses.add(MockResponse.Builder().code(500).build())
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())
        detailsResponses.add(MockResponse.Builder().body("not json").build())

        val afterHttpError = repository.getGameStats("1")!!
        val afterBadBody = repository.getGameStats("1")!!

        assertEquals(31850, afterHttpError.blueTeam.totalGold)
        assertEquals(31850, afterBadBody.blueTeam.totalGold)
        assertTrue(afterHttpError.blueTeam.players.all { it.details == null })
        assertTrue(afterBadBody.blueTeam.players.all { it.details == null })
    }

    @Test
    fun `no window frames means no stats whatever the details feed says`() = runBlocking {
        windowResponses.add(MockResponse.Builder().code(204).build())
        detailsResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_details.json")).build())

        assertNull(repository.getGameStats("1"))
    }

    @Test
    fun `a window error is still thrown whatever the details feed says`() = runBlocking {
        windowResponses.add(MockResponse.Builder().code(404).build())
        detailsResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_details.json")).build())

        try {
            repository.getGameStats("1")
            fail("Expected an exception")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }

    @Test
    fun `without the details feed the stats are the window stats`() = runBlocking {
        windowResponses.add(MockResponse.Builder().body(Fixtures.read("livestats_window.json")).build())

        val stats = repository.getGameStats("115660588583040711")!!

        assertEquals(listOf("Olaf", "Vi", "Cassiopeia", "Ashe", "Seraphine"), stats.blueTeam.players.map { it.championId })
        assertEquals(31850, stats.blueTeam.totalGold)
        assertTrue((stats.blueTeam.players + stats.redTeam.players).all { it.details == null })
    }
}
