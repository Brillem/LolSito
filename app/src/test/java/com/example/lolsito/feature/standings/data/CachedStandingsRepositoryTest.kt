package com.example.lolsito.feature.standings.data

import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CachedStandingsRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-10-20T12:00:00Z"))
    private val source = FakeSource()
    private val repository = CachedStandingsRepository(source, clock)

    @Test
    fun `tournaments are cached for an hour`() = runTest {
        repository.getTournaments(LEAGUE)
        clock.advance(Duration.ofMinutes(59))
        repository.getTournaments(LEAGUE)
        assertEquals(listOf("t:$LEAGUE"), source.requests)

        clock.advance(Duration.ofMinutes(2))
        repository.getTournaments(LEAGUE)
        assertEquals(listOf("t:$LEAGUE", "t:$LEAGUE"), source.requests)
    }

    @Test
    fun `tournaments are cached per league`() = runTest {
        repository.getTournaments(LEAGUE)
        repository.getTournaments("other")
        repository.getTournaments(LEAGUE)

        assertEquals(listOf("t:$LEAGUE", "t:other"), source.requests)
    }

    @Test
    fun `standings of a tournament not known to be finished expire after two minutes`() = runTest {
        repository.getStandings(ONGOING)
        clock.advance(Duration.ofSeconds(119))
        repository.getStandings(ONGOING)
        assertEquals(listOf("s:$ONGOING"), source.requests)

        clock.advance(Duration.ofSeconds(2))
        repository.getStandings(ONGOING)
        assertEquals(listOf("s:$ONGOING", "s:$ONGOING"), source.requests)
    }

    @Test
    fun `standings of a tournament that ended before today are kept for good`() = runTest {
        repository.getTournaments(LEAGUE)
        repository.getStandings(FINISHED)
        clock.advance(Duration.ofDays(30))
        repository.getStandings(FINISHED)

        assertEquals(listOf("t:$LEAGUE", "s:$FINISHED"), source.requests)
    }

    @Test
    fun `standings of a tournament ending today use the short ttl`() = runTest {
        repository.getTournaments(LEAGUE)
        repository.getStandings(ONGOING)
        clock.advance(Duration.ofMinutes(3))
        repository.getStandings(ONGOING)

        assertEquals(listOf("t:$LEAGUE", "s:$ONGOING", "s:$ONGOING"), source.requests)
    }

    @Test
    fun `a cached value is returned while it is fresh even if the source changed`() = runTest {
        val first = repository.getStandings(ONGOING)
        source.standings[ONGOING] = standings("changed")

        assertEquals(first, repository.getStandings(ONGOING))
    }

    @Test
    fun `a failure is not cached`() = runTest {
        source.error = IOException("offline")
        try {
            repository.getStandings(ONGOING)
            fail("Expected the failure")
        } catch (e: IOException) {
            // Expected.
        }
        try {
            repository.getTournaments(LEAGUE)
            fail("Expected the failure")
        } catch (e: IOException) {
            // Expected.
        }

        source.error = null
        assertEquals(standings(ONGOING), repository.getStandings(ONGOING))
        assertEquals(TOURNAMENTS, repository.getTournaments(LEAGUE))
        assertEquals(listOf("s:$ONGOING", "t:$LEAGUE"), source.requests)
    }

    @Test
    fun `refreshStandings skips the cache and keeps the fresh standings`() = runTest {
        repository.getTournaments(LEAGUE)
        repository.getStandings(FINISHED)
        source.standings[FINISHED] = standings("fresh")

        assertEquals(standings("fresh"), repository.refreshStandings(FINISHED))
        assertEquals(standings("fresh"), repository.getStandings(FINISHED))
        assertEquals(listOf("t:$LEAGUE", "s:$FINISHED", "s:$FINISHED"), source.requests)
    }

    @Test
    fun `a failed refresh keeps the cached standings`() = runTest {
        repository.getStandings(ONGOING)
        source.error = IOException("offline")
        try {
            repository.refreshStandings(ONGOING)
            fail("Expected the failure")
        } catch (e: IOException) {
            // Expected.
        }
        source.error = null

        assertEquals(standings(ONGOING), repository.getStandings(ONGOING))
        assertEquals(listOf("s:$ONGOING"), source.requests)
    }

    @Test
    fun `concurrent identical requests share one call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate

        val a = async { repository.getStandings(ONGOING) }
        val b = async { repository.getStandings(ONGOING) }
        runCurrent()
        gate.complete(Unit)

        assertEquals(a.await(), b.await())
        assertEquals(listOf("s:$ONGOING"), source.requests)
    }

    private class MutableClock(var instant: Instant) : Clock() {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun instant(): Instant = instant
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private class FakeSource : StandingsRepository {
        val requests = mutableListOf<String>()
        val standings = mutableMapOf<String, Standings>()
        var error: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getTournaments(leagueId: String): List<Tournament> {
            gate?.await()
            error?.let { throw it }
            requests += "t:$leagueId"
            return TOURNAMENTS
        }

        override suspend fun getStandings(tournamentId: String): Standings {
            gate?.await()
            error?.let { throw it }
            requests += "s:$tournamentId"
            return standings[tournamentId] ?: standings(tournamentId)
        }
    }

    private companion object {
        const val LEAGUE = "lck"
        const val ONGOING = "lck-fall"
        const val FINISHED = "lck-spring"

        val TOURNAMENTS = listOf(
            Tournament(ONGOING, ONGOING, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-20")),
            Tournament(FINISHED, FINISHED, LocalDate.parse("2026-01-10"), LocalDate.parse("2026-04-10")),
        )

        fun standings(name: String) = Standings(listOf(StandingsStage(name, name, emptyList())))
    }
}
