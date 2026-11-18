package com.example.lolsito.feature.standings.data

import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** How long a league's tournament list is kept. */
val TOURNAMENTS_TTL: Duration = Duration.ofHours(1)

/** How long the standings of a tournament that may still be played are kept. */
val LIVE_STANDINGS_TTL: Duration = Duration.ofMinutes(2)

/**
 * Keeps [source]'s answers in memory for the app's lifetime, following the pattern of
 * [com.example.lolsito.core.leagues.CachedLeaguesRepository]:
 * - a league's tournaments for [TOURNAMENTS_TTL];
 * - a tournament's standings for good once the tournament is finished, for [LIVE_STANDINGS_TTL] otherwise.
 *   A tournament is finished when a tournament list fetched through this cache gave it an end date before
 *   today ([clock]); one never seen in a list, or without an end date, counts as not finished.
 *
 * A failure (or a cancelled caller) caches nothing. Callers asking for the same key wait on one lock per key,
 * so only one request is in flight and the others reuse its result. [refreshStandings] skips the cache and
 * keeps what it gets; when it fails, the cached standings stay.
 */
class CachedStandingsRepository(
    private val source: StandingsRepository,
    private val clock: Clock,
) : StandingsRepository {

    private class Entry<T>(val value: T, val expiresAt: Instant?) {
        fun isFresh(now: Instant): Boolean = expiresAt == null || now.isBefore(expiresAt)
    }

    private val tournaments = ConcurrentHashMap<String, Entry<List<Tournament>>>()
    private val standings = ConcurrentHashMap<String, Entry<Standings>>()

    /** End dates of every tournament seen in a fetched list, by tournament id. */
    private val endDates = ConcurrentHashMap<String, LocalDate>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun getTournaments(leagueId: String): List<Tournament> =
        cachedOrFetch(tournaments, "t:$leagueId", leagueId) {
            source.getTournaments(leagueId).also { list ->
                list.forEach { t -> t.endDate?.let { endDates[t.id] = it } }
            }.let { Entry(it, clock.instant().plus(TOURNAMENTS_TTL)) }
        }

    override suspend fun getStandings(tournamentId: String): Standings =
        cachedOrFetch(standings, "s:$tournamentId", tournamentId) { standingsEntry(source.getStandings(tournamentId), tournamentId) }

    override suspend fun refreshStandings(tournamentId: String): Standings = lockFor("s:$tournamentId").withLock {
        standingsEntry(source.refreshStandings(tournamentId), tournamentId).also { standings[tournamentId] = it }.value
    }

    private fun standingsEntry(value: Standings, tournamentId: String): Entry<Standings> {
        val end = endDates[tournamentId]
        val finished = end != null && end.isBefore(LocalDate.now(clock))
        return Entry(value, if (finished) null else clock.instant().plus(LIVE_STANDINGS_TTL))
    }

    private suspend fun <T> cachedOrFetch(
        cache: ConcurrentHashMap<String, Entry<T>>,
        lockKey: String,
        key: String,
        fetch: suspend () -> Entry<T>,
    ): T {
        cache[key]?.takeIf { it.isFresh(clock.instant()) }?.let { return it.value }
        return lockFor(lockKey).withLock {
            cache[key]?.takeIf { it.isFresh(clock.instant()) }?.value ?: fetch().also { cache[key] = it }.value
        }
    }

    private fun lockFor(key: String): Mutex = locks.computeIfAbsent(key) { Mutex() }
}
