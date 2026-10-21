package com.example.lolsito.core.leagues

import com.example.lolsito.core.model.League
import com.example.lolsito.core.network.LolEsportsApi

/** Fetches the leagues from the API on every call; wrap it in [CachedLeaguesRepository] to share them. */
class RemoteLeaguesRepository(private val api: LolEsportsApi) : LeaguesRepository {
    override suspend fun getLeagues(): List<League> =
        api.getLeagues().data.leagues.toDomainLeagues()
}
