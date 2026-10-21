package com.example.lolsito.core.leagues

import com.example.lolsito.core.model.League
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CachedLeaguesRepositoryTest {

    private val leagues = listOf(League("1", "lck", "LCK", "KOREA", null))
    private val source = FakeLeaguesSource(leagues)
    private val repository = CachedLeaguesRepository(source)

    @Test
    fun `a second call returns the cached leagues without another request`() = runTest {
        val first = repository.getLeagues()
        val second = repository.getLeagues()

        assertEquals(leagues, first)
        assertSame(first, second)
        assertEquals(1, source.requests)
    }

    @Test
    fun `concurrent calls share one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate

        val a = async { repository.getLeagues() }
        val b = async { repository.getLeagues() }
        runCurrent()
        gate.complete(Unit)

        assertEquals(leagues, a.await())
        assertEquals(leagues, b.await())
        assertEquals(1, source.requests)
    }

    @Test
    fun `a failure is not cached`() = runTest {
        source.error = IOException("down")
        try {
            repository.getLeagues()
            fail("expected the failure to propagate")
        } catch (e: IOException) {
            assertEquals("down", e.message)
        }

        try {
            repository.getLeagues()
            fail("expected the failure to propagate again")
        } catch (_: IOException) {
        }

        assertEquals("every call after a failure hits the source again", 2, source.requests)
    }

    @Test
    fun `a retry after a failure succeeds and is then cached`() = runTest {
        source.error = IOException("down")
        val failed = runCatching { repository.getLeagues() }
        assertTrue(failed.exceptionOrNull() is IOException)

        source.error = null
        assertEquals(leagues, repository.getLeagues())
        assertEquals(leagues, repository.getLeagues())

        assertEquals(2, source.requests)
    }

    private class FakeLeaguesSource(private val leagues: List<League>) : LeaguesRepository {
        var requests = 0
        var error: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getLeagues(): List<League> {
            requests++
            gate?.await()
            error?.let { throw it }
            return leagues
        }
    }
}
