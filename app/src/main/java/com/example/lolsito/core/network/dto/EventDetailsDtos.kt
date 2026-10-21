package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EventDetailsResponse(val data: EventDetailsData = EventDetailsData())

@Serializable
data class EventDetailsData(val event: EventDetailDto? = null)

@Serializable
data class EventDetailDto(
    val id: String? = null,
    val type: String? = null,
    val blockName: String? = null,
    val league: EventDetailLeagueDto? = null,
    val tournament: TournamentRefDto? = null,
    val match: EventDetailMatchDto? = null,
)

@Serializable
data class EventDetailLeagueDto(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val image: String? = null,
)

@Serializable
data class TournamentRefDto(val id: String? = null)

@Serializable
data class EventDetailMatchDto(
    val strategy: StrategyDto? = null,
    val teams: List<EventDetailTeamDto>? = null,
    val games: List<GameDto>? = null,
)

@Serializable
data class EventDetailTeamDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    val image: String? = null,
    val result: ResultDto? = null,
)

@Serializable
data class GameDto(
    val number: Int? = null,
    val id: String? = null,
    val state: String? = null,
    val teams: List<GameTeamDto>? = null,
)

@Serializable
data class GameTeamDto(val id: String? = null, val side: String? = null)
