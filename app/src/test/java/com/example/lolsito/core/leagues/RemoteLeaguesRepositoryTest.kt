package com.example.lolsito.core.leagues

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RemoteLeaguesRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: RemoteLeaguesRepository

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = RemoteLeaguesRepository(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `getLeagues returns sorted domain leagues`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getLeagues.json")).build())

        val leagues = repository.getLeagues()

        assertEquals("worlds", leagues.first().slug)
    }
}
