package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament

class StandingsRepositoryImpl(private val api: LolEsportsApi) : StandingsRepository {
    override suspend fun getTournaments(leagueId: String): List<Tournament> =
        api.getTournamentsForLeague(leagueId).toDomainTournaments()

    override suspend fun getStandings(tournamentId: String): Standings =
        api.getStandings(tournamentId).toDomainStandings()
}
