package com.example.lolsito.core.network

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.dto.EventDetailTeamDto
import com.example.lolsito.core.network.dto.ResultDto
import com.example.lolsito.core.network.dto.StandingsTeamDto
import com.example.lolsito.core.network.dto.TeamDto

/** Schedule team; the schedule sends no team id, so [Team.id] stays null. */
fun TeamDto.toTeam(): Team = buildTeam(name, code, image, result, id = null)

fun EventDetailTeamDto.toTeam(): Team = buildTeam(name, code, image, result, id)

fun StandingsTeamDto.toTeam(): Team = buildTeam(name, code, image, result, id)

/**
 * The single team mapping shared by every endpoint: a missing name or blank code reads "TBD",
 * logos are forced to https, and a blank id is treated as missing.
 */
private fun buildTeam(name: String?, code: String?, image: String?, result: ResultDto?, id: String?): Team = Team(
    name = name ?: "TBD",
    code = code?.takeIf { it.isNotBlank() } ?: "TBD",
    imageUrl = image.toHttps(),
    outcome = result?.outcome.toOutcome(),
    gameWins = result?.gameWins,
    id = id?.takeIf { it.isNotBlank() },
)
