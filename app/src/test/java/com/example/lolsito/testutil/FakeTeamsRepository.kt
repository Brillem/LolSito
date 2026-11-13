package com.example.lolsito.testutil

import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository

/** Knows the teams in [profiles] by id; any other id is an unknown team. */
class FakeTeamsRepository : TeamsRepository {
    val profiles = mutableMapOf<String, TeamProfile>()
    val requests = mutableListOf<String>()
    var error: Throwable? = null

    override suspend fun getTeam(id: String): TeamProfile? {
        requests += id
        error?.let { throw it }
        return profiles[id]
    }
}
