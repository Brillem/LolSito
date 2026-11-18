package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The DTO to domain mapping runs on [mapperDispatcher], off the main thread. */
class StandingsRepositoryImpl(
    private val api: LolEsportsApi,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StandingsRepository {
    override suspend fun getTournaments(leagueId: String): List<Tournament> {
        val response = api.getTournamentsForLeague(leagueId)
        return withContext(mapperDispatcher) { response.toDomainTournaments() }
    }

    override suspend fun getStandings(tournamentId: String): Standings {
        val response = api.getStandingsV3(tournamentId)
        return withContext(mapperDispatcher) { response.toDomainStandings() }
    }
}
