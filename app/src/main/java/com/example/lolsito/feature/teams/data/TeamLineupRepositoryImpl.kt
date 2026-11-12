package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.teams.domain.TeamLineupRepository
import com.example.lolsito.feature.teams.domain.lastPlayedGameId

/** Empty startingTime: the feed answers with the first frames of the game, which already carry its metadata. */
private const val FIRST_FRAMES_STARTING_TIME = ""

class TeamLineupRepositoryImpl(
    private val matchDetails: MatchDetailRepository,
    private val liveStats: LiveStatsApi,
) : TeamLineupRepository {

    override suspend fun getLineup(matchId: String, teamId: String): Set<String>? {
        val gameId = matchDetails.getMatchDetail(matchId).lastPlayedGameId() ?: return null
        val response = liveStats.getWindow(gameId, FIRST_FRAMES_STARTING_TIME)
        if (!response.isSuccessful) return null
        return response.body()?.lineupOf(teamId)
    }
}

/** The player ids of the team [teamId] in this game, or null when the team is not in it or no ids are sent. */
fun LiveStatsWindowResponse.lineupOf(teamId: String): Set<String>? {
    val metadata = gameMetadata ?: return null
    val team = listOfNotNull(metadata.blueTeamMetadata, metadata.redTeamMetadata).firstOrNull { it.esportsTeamId == teamId }
    return team?.participantMetadata.orEmpty()
        .mapNotNull { it.esportsPlayerId?.takeIf(String::isNotBlank) }
        .toSet()
        .takeIf { it.isNotEmpty() }
}
