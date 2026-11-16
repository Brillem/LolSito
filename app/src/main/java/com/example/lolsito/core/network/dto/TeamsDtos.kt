package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TeamsResponse(val data: TeamsData = TeamsData())

@Serializable
data class TeamsData(val teams: List<TeamDetailDto> = emptyList())

/** One team as sent by getTeams, with its whole player list. */
@Serializable
data class TeamDetailDto(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val code: String? = null,
    val image: String? = null,
    /** "active" for a team still playing; any other value (or none) is a team that is not. */
    val status: String? = null,
    val homeLeague: HomeLeagueDto? = null,
    val players: List<PlayerDto> = emptyList(),
)

@Serializable
data class HomeLeagueDto(val name: String? = null, val region: String? = null)

@Serializable
data class PlayerDto(
    val id: String? = null,
    val summonerName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val image: String? = null,
    val role: String? = null,
)
