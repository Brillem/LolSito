package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The DTO to domain mapping runs on [mapperDispatcher], off the main thread. */
class TeamsRepositoryImpl(
    private val api: LolEsportsApi,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TeamsRepository {
    override suspend fun getTeam(id: String): TeamProfile? {
        val response = api.getTeams(id)
        return withContext(mapperDispatcher) { response.toTeamProfile() }
    }
}
