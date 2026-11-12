package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.dto.PlayerDto
import com.example.lolsito.core.network.dto.TeamDetailDto
import com.example.lolsito.core.network.dto.TeamsResponse
import com.example.lolsito.core.network.toHttps
import com.example.lolsito.feature.teams.domain.Player
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.feature.teams.domain.RoleGroup
import com.example.lolsito.feature.teams.domain.TeamProfile

/** The first team of the answer, or null when there is none or it has no id. */
fun TeamsResponse.toTeamProfile(): TeamProfile? = data.teams.firstOrNull()?.toDomain()

private fun TeamDetailDto.toDomain(): TeamProfile? {
    val teamId = id?.takeIf { it.isNotBlank() } ?: return null
    val players = players.map { it.toDomain() }
    return TeamProfile(
        id = teamId,
        slug = slug.orEmpty(),
        name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "TBD",
        code = code?.trim()?.takeIf { it.isNotEmpty() } ?: "TBD",
        imageUrl = image.toHttps(),
        homeLeagueName = homeLeague?.name?.takeIf { it.isNotBlank() },
        homeLeagueRegion = homeLeague?.region?.takeIf { it.isNotBlank() },
        // Every role keeps the API order of its players.
        roster = PlayerRole.entries.mapNotNull { role ->
            players.filter { it.role == role }.takeIf { it.isNotEmpty() }?.let { RoleGroup(role, it) }
        },
    )
}

private fun PlayerDto.toDomain(): Player = Player(
    id = id?.takeIf { it.isNotBlank() },
    summonerName = summonerName?.trim().orEmpty(),
    firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
    lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
    imageUrl = image.toHttps(),
    role = role.toPlayerRole(),
)

private fun String?.toPlayerRole(): PlayerRole = when (this?.trim()?.lowercase()) {
    "top" -> PlayerRole.TOP
    "jungle" -> PlayerRole.JUNGLE
    "mid" -> PlayerRole.MID
    "bottom" -> PlayerRole.BOTTOM
    "support" -> PlayerRole.SUPPORT
    else -> PlayerRole.OTHER
}
