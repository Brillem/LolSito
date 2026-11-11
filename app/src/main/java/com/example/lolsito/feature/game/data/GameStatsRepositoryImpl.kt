package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import retrofit2.HttpException
import java.time.Clock

class GameStatsRepositoryImpl(private val api: LiveStatsApi, private val clock: Clock) : GameStatsRepository {
    /** A 204 (no body) or a body without frames both mean the game has no stats. */
    override suspend fun getGameStats(gameId: String): GameStats? {
        val response = api.getWindow(gameId, latestStartingTime(clock.instant()))
        if (!response.isSuccessful) throw HttpException(response)
        return response.body()?.toGameStats()
    }
}
