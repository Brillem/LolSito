package com.example.lolsito.feature.match.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The DTO to domain mapping runs on [mapperDispatcher], off the main thread. */
class MatchDetailRepositoryImpl(
    private val api: LolEsportsApi,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MatchDetailRepository {
    override suspend fun getMatchDetail(matchId: String): MatchDetail {
        val response = api.getEventDetails(matchId)
        return withContext(mapperDispatcher) { response.toMatchDetail() }
            ?: throw NoSuchElementException("Match $matchId not found")
    }
}
