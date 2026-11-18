package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.NetworkModule
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class TeamDirectoryRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: TeamDirectoryRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule.createApi(
            server.url("/persisted/gw/").toString(),
            NetworkModule.createOkHttpClient(OkHttpClient.Builder()),
        )
        repository = TeamDirectoryRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `the search index is built once from one request and searches the team list`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getTeams_t1.json")).build())

        val first = repository.getSearchIndex()
        val second = repository.getSearchIndex()

        assertSame(first, second)
        assertEquals(1, server.requestCount)
        assertEquals(listOf("T1"), first.search("t1").teams.map { it.code })
        assertEquals(repository.getAllTeams().first(), first.search("t1").teams.single())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed request is not kept`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().body(Fixtures.read("getTeams_t1.json")).build())

        runCatching { repository.getSearchIndex() }

        assertEquals(listOf("T1"), repository.getSearchIndex().search("t1").teams.map { it.code })
        assertEquals(2, server.requestCount)
    }
}
