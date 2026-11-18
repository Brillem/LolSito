package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.game.domain.GoldHistory
import com.example.lolsito.feature.game.domain.GoldSample
import com.example.lolsito.feature.game.domain.winnerTeamId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Longest the stats wait, once the window feed has answered, for the details feed. */
val DETAILS_WAIT_LIMIT = 3.seconds

/** Most gold sample windows requested at once. */
const val GOLD_SAMPLE_CONCURRENCY = 3

/**
 * startingTime that makes the window feed answer with a game's first frames. Observed on the feed: an
 * empty startingTime is answered as if there were none, with the frames at the start of the game.
 */
internal const val FIRST_FRAMES_STARTING_TIME = ""

/** Most games whose gold samples, and most games whose winner, are kept in memory. */
const val MAX_CACHED_GAMES = 20

/**
 * The DTO to domain mapping runs on [mapperDispatcher], off the main thread. The gold samples and the
 * winners are kept for the [maxCachedGames] most recently used games each; older ones are asked for again.
 */
class GameStatsRepositoryImpl(
    private val api: LiveStatsApi,
    private val clock: Clock,
    maxCachedGames: Int = MAX_CACHED_GAMES,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GameStatsRepository {

    /** Gold samples already fetched, by game id. */
    private val goldCaches = LruCache<String, GoldCache>(maxCachedGames)

    /** Winner team id by game id, of the games whose winner is known. */
    private val winners = LruCache<String, String>(maxCachedGames)

    private class GoldCache {
        @Volatile
        var start: GoldSample? = null

        /** By the startingTime of the window each sample came from. */
        val samples = ConcurrentHashMap<String, GoldSample>()
    }
    /**
     * A 204 (no body) or a body without frames both mean the game has no stats. Details are
     * secondary: they are requested for the same window as the stats, at the same time, and when that
     * fails, has nothing or is still unanswered [DETAILS_WAIT_LIMIT] after the stats arrived, the
     * stats come back without them. A window failure or a cancellation cancels the details request too.
     */
    override suspend fun getGameStats(gameId: String): GameStats? = coroutineScope {
        val startingTime = latestStartingTime(clock.instant())
        val details = async { getDetails(gameId, startingTime) }
        val response = api.getWindow(gameId, startingTime)
        if (!response.isSuccessful) throw HttpException(response)
        val stats = response.body()?.let { body -> withContext(mapperDispatcher) { body.toGameStats() } }
        if (stats == null) {
            details.cancel()
            return@coroutineScope null
        }
        val gameDetails = withTimeoutOrNull(DETAILS_WAIT_LIMIT) { details.await() }
        if (gameDetails == null) {
            details.cancel()
            return@coroutineScope stats
        }
        stats.withDetails(gameDetails.players, gameDetails.frameTime ?: stats.frameTime)
    }

    /**
     * Only the window feed is asked: the details add nothing to who won. A winner never changes, so one
     * found is kept for as long as this repository lives; an unknown one is asked for again.
     */
    override suspend fun getWinnerTeamId(gameId: String): String? {
        winners.get(gameId)?.let { return it }
        val response = api.getWindow(gameId, latestStartingTime(clock.instant()))
        if (!response.isSuccessful) throw HttpException(response)
        val body = response.body() ?: return null
        return withContext(mapperDispatcher) { body.toGameStats()?.winnerTeamId() }?.also { winners.put(gameId, it) }
    }

    /**
     * The window feed holds about 10 seconds per request, so the history is sampled: the game's first
     * frames give its start, then one window every [GOLD_SAMPLE_INTERVAL] up to [until] (see
     * [goldSampleTimes]), at most [GOLD_SAMPLE_CONCURRENCY] at once. Samples are kept per game, so a
     * later call (a live game that went on) only asks for the windows it has not got yet. A window that
     * fails or has no frames is left out of this history and asked for again next time; without the
     * first frames there is no history.
     */
    override suspend fun getGoldHistory(gameId: String, until: Instant): GoldHistory? {
        val cache = goldCaches.getOrPut(gameId) { GoldCache() }
        val start = cache.start
            ?: fetchGoldSample(gameId, FIRST_FRAMES_STARTING_TIME) { it.firstGoldSample() }?.also { cache.start = it }
            ?: return null
        val times = goldSampleTimes(start.frameTime, until)
        val permits = Semaphore(GOLD_SAMPLE_CONCURRENCY)
        coroutineScope {
            times.filterNot(cache.samples::containsKey).map { startingTime ->
                launch {
                    permits.withPermit { fetchGoldSample(gameId, startingTime) { it.lastGoldSample() } }
                        ?.let { cache.samples[startingTime] = it }
                }
            }
        }
        return GoldHistory.of(start.frameTime, listOf(start) + times.mapNotNull(cache.samples::get))
    }

    private suspend fun fetchGoldSample(
        gameId: String,
        startingTime: String,
        sample: (LiveStatsWindowResponse) -> GoldSample?,
    ): GoldSample? = try {
        val response = api.getWindow(gameId, startingTime)
        val body = if (response.isSuccessful) response.body() else null
        body?.let { withContext(mapperDispatcher) { sample(it) } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun getDetails(gameId: String, startingTime: String) = try {
        val response = api.getDetails(gameId, startingTime)
        val body = if (response.isSuccessful) response.body() else null
        body?.let { withContext(mapperDispatcher) { it.toGameDetails() } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** A map that keeps its [maxEntries] most recently used entries; every access is synchronized. */
internal class LruCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
    }

    fun get(key: K): V? = synchronized(map) { map[key] }

    fun put(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }

    fun getOrPut(key: K, create: () -> V): V = synchronized(map) { map.getOrPut(key, create) }
}
