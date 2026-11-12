package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository

class TeamsRepositoryImpl(private val api: LolEsportsApi) : TeamsRepository {
    override suspend fun getTeam(id: String): TeamProfile? = api.getTeams(id).toTeamProfile()
}
