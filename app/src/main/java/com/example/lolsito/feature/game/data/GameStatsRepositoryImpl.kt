package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Longest the stats wait, once the window feed has answered, for the details feed. */
val DETAILS_WAIT_LIMIT = 3.seconds

class GameStatsRepositoryImpl(private val api: LiveStatsApi, private val clock: Clock) : GameStatsRepository {
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
        val stats = response.body()?.toGameStats()
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

    private suspend fun getDetails(gameId: String, startingTime: String) = try {
        val response = api.getDetails(gameId, startingTime)
        if (response.isSuccessful) response.body()?.toGameDetails() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
