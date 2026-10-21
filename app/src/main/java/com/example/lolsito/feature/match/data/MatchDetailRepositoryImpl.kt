package com.example.lolsito.feature.match.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository

class MatchDetailRepositoryImpl(private val api: LolEsportsApi) : MatchDetailRepository {
    override suspend fun getMatchDetail(matchId: String): MatchDetail =
        api.getEventDetails(matchId).toMatchDetail()
            ?: throw NoSuchElementException("Match $matchId not found")
}
