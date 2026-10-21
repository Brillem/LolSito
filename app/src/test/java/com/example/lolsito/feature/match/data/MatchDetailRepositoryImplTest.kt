package com.example.lolsito.feature.match.data

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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MatchDetailRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: MatchDetailRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = MatchDetailRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getMatchDetail requests the match id and maps the result`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getEventDetails.json")).build())

        val detail = repository.getMatchDetail("115660588583040710")

        val request = server.takeRequest()
        assertEquals("/getEventDetails", request.url.encodedPath)
        assertEquals("115660588583040710", request.url.queryParameter("id"))
        assertEquals(ApiConfig.API_KEY, request.headers["x-api-key"])
        assertEquals("115660588583040710", detail.matchId)
        assertEquals(listOf("MVK", "FUR"), detail.teams.map { it.code })
        assertEquals("3 - 0", detail.result.score)
        assertEquals("Bo5", detail.result.bestOf)
    }

    @Test
    fun `getMatchDetail throws when the event has no match`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"data":{"event":null}}""").build())

        try {
            repository.getMatchDetail("1")
            fail("Expected an exception")
        } catch (e: NoSuchElementException) {
            assertTrue(e.message!!.contains("1"))
        }
    }
}
