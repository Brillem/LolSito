package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.Clock

class GameStatsRepositoryImpl(private val api: LiveStatsApi, private val clock: Clock) : GameStatsRepository {
    /**
     * A 204 (no body) or a body without frames both mean the game has no stats. Details are
     * secondary: they are requested for the same window as the stats, and when that fails or has
     * nothing the stats come back without them.
     */
    override suspend fun getGameStats(gameId: String): GameStats? {
        val startingTime = latestStartingTime(clock.instant())
        val response = api.getWindow(gameId, startingTime)
        if (!response.isSuccessful) throw HttpException(response)
        val stats = response.body()?.toGameStats() ?: return null
        val details = getDetails(gameId, startingTime) ?: return stats
        return stats.withDetails(details)
    }

    private suspend fun getDetails(gameId: String, startingTime: String) = try {
        val response = api.getDetails(gameId, startingTime)
        if (response.isSuccessful) response.body()?.toPlayerDetails() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
