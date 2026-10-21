package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ScheduleRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: ScheduleRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = ScheduleRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getSchedule returns page with tokens and joins league ids`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getSchedule_worlds.json")).build())

        val page = repository.getSchedule(listOf("a", "b"), pageToken = null)

        assertEquals(80, page.events.size)
        assertEquals("b2xkZXI6OjExMzQ3NTc5ODAwNjY2NDU3NA==", page.olderToken)
        assertEquals("bmV3ZXI6OjExNTY2MDU0MDcyNjA5NTEwOQ==", page.newerToken)
        val request = server.takeRequest()
        assertEquals("a,b", request.url.queryParameter("leagueId"))
        assertNull(request.url.queryParameter("pageToken"))
    }
}
