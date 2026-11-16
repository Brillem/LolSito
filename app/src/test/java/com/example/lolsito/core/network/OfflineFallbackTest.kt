package com.example.lolsito.core.network

import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant

class OfflineFallbackTest {

    @get:Rule
    val cacheDirectory = TemporaryFolder()

    private val server = MockWebServer()
    private val offlineState = OfflineState()

    /** Flipped off to make every request fail below the cache, as a device without a connection does. */
    private var networkUp = true

    /** Flipped on to cancel every call while its request is on the way, as a cancelled coroutine does. */
    private var cancelInFlight = false
    private val baseClient = OkHttpClient.Builder()
        .addNetworkInterceptor(
            Interceptor { chain ->
                if (cancelInFlight) {
                    chain.call().cancel()
                    throw IOException("Canceled")
                }
                if (!networkUp) throw UnknownHostException("no network")
                chain.proceed(chain.request())
            }
        )
        .build()

    private lateinit var gatewayClient: OkHttpClient
    private lateinit var api: LolEsportsApi

    @Before
    fun setUp() {
        server.start()
        gatewayClient = NetworkModule.createOkHttpClient(
            baseClient.newBuilder(),
            OfflineCache(cacheDirectory.root, offlineState),
        )
        api = NetworkModule.createApi(baseUrl = server.url("/persisted/gw/").toString(), client = gatewayClient)
    }

    @After
    fun tearDown() {
        gatewayClient.cache?.close()
        server.close()
    }

    /** As the gateway answers: storable, fresh for a minute. */
    private fun storable(fixture: String) = MockResponse.Builder()
        .addHeader("Cache-Control", "max-age=60, public")
        .body(Fixtures.read(fixture))
        .build()

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun `with a network the answer comes from the network even when a copy is stored`() = runTest {
        server.enqueue(storable("getLive.json"))
        server.enqueue(storable("getSchedule_worlds.json"))

        val first = api.getSchedule(leagueIds = "1")
        val second = api.getSchedule(leagueIds = "1")

        assertEquals(0, first.data.schedule.events.size)
        assertEquals(80, second.data.schedule.events.size)
        assertEquals(2, server.requestCount)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `without a network the stored copy is returned and the state turns offline with its time`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        val before = Instant.now()
        api.getSchedule(leagueIds = "1")
        val after = Instant.now()

        networkUp = false
        val copy = api.getSchedule(leagueIds = "1")

        assertEquals(80, copy.data.schedule.events.size)
        assertEquals(1, server.requestCount)
        val cachedSince: Instant? = offlineState.cachedSince.value
        assertNotNull(cachedSince)
        assertTrue(!cachedSince!!.isBefore(before.minusMillis(1)) && !cachedSince.isAfter(after))
    }

    @Test
    fun `a stored copy belongs to its own request only`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        api.getSchedule(leagueIds = "1")

        networkUp = false
        val failure = failureOf { api.getSchedule(leagueIds = "2") }

        assertTrue(failure is IOException)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `without a network and without a copy the call fails as before`() = runTest {
        networkUp = false

        val failure = failureOf { api.getLeagues() }

        assertTrue(failure is UnknownHostException)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `once the network is back the state stops being offline`() = runTest {
        server.enqueue(storable("getLive.json"))
        server.enqueue(storable("getSchedule_worlds.json"))
        api.getSchedule(leagueIds = "1")
        networkUp = false
        api.getSchedule(leagueIds = "1")
        assertNotNull(offlineState.cachedSince.value)

        networkUp = true
        val fresh = api.getSchedule(leagueIds = "1")

        assertEquals(80, fresh.data.schedule.events.size)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `getLive is never served from a stored copy`() = runTest {
        server.enqueue(storable("getLive_live.json"))
        api.getLive()

        networkUp = false
        val failure = failureOf { api.getLive() }

        assertTrue(failure is UnknownHostException)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `a failed getLive leaves an offline state as it was`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        api.getSchedule(leagueIds = "1")
        networkUp = false
        api.getSchedule(leagueIds = "1")
        val cachedSince = offlineState.cachedSince.value

        failureOf { api.getLive() }

        assertNotNull(cachedSince)
        assertEquals(cachedSince, offlineState.cachedSince.value)
    }

    @Test
    fun `a server error is not replaced by the stored copy`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        server.enqueue(MockResponse.Builder().code(500).build())
        api.getSchedule(leagueIds = "1")

        val failure = failureOf { api.getSchedule(leagueIds = "1") }

        assertEquals(500, (failure as HttpException).code())
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `an error answer the cache could store does not replace the stored copy`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        server.enqueue(MockResponse.Builder().code(404).addHeader("Cache-Control", "max-age=60, public").build())
        api.getSchedule(leagueIds = "1")
        val failure = failureOf { api.getSchedule(leagueIds = "1") }
        assertEquals(404, (failure as HttpException).code())

        networkUp = false
        val copy = api.getSchedule(leagueIds = "1")

        assertEquals(80, copy.data.schedule.events.size)
        assertNotNull(offlineState.cachedSince.value)
    }

    @Test
    fun `a cancelled call is not answered from the stored copy and leaves the state online`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))
        api.getSchedule(leagueIds = "1")

        cancelInFlight = true
        val failure = failureOf { api.getSchedule(leagueIds = "1") }

        assertTrue(failure is IOException)
        assertNull(offlineState.cachedSince.value)
    }

    @Test
    fun `the api key and the locale are still sent`() = runTest {
        server.enqueue(storable("getSchedule_worlds.json"))

        api.getSchedule(leagueIds = "1,2", pageToken = "tok")

        val request = server.takeRequest()
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("en-US", request.url.queryParameter("hl"))
        assertEquals("1,2", request.url.queryParameter("leagueId"))
        assertEquals("tok", request.url.queryParameter("pageToken"))
    }

    @Test
    fun `the cache belongs to the gateway client only, so the livestats feed has none`() {
        assertNotNull(gatewayClient.cache)
        assertNull(baseClient.cache)
    }

    @Test
    fun `a client built without the offline cache never answers from a copy`() = runTest {
        val networkOnly = NetworkModule.createApi(
            baseUrl = server.url("/persisted/gw/").toString(),
            client = NetworkModule.createOkHttpClient(baseClient.newBuilder()),
        )
        server.enqueue(storable("getSchedule_worlds.json"))
        server.enqueue(storable("getSchedule_worlds.json"))
        api.getSchedule(leagueIds = "1")
        networkOnly.getSchedule(leagueIds = "1")

        networkUp = false
        val failure = failureOf { networkOnly.getSchedule(leagueIds = "1") }

        assertTrue(failure is UnknownHostException)
        assertNull(offlineState.cachedSince.value)
    }
}
