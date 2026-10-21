package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentsResponse(val data: TournamentsData = TournamentsData())

@Serializable
data class TournamentsData(val leagues: List<LeagueTournamentsDto> = emptyList())

@Serializable
data class LeagueTournamentsDto(val tournaments: List<TournamentDto> = emptyList())

@Serializable
data class TournamentDto(
    val id: String? = null,
    val slug: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

@Serializable
data class StandingsResponse(val data: StandingsData = StandingsData())

@Serializable
data class StandingsData(val standings: List<StandingDto> = emptyList())

@Serializable
data class StandingDto(val stages: List<StageDto> = emptyList())

@Serializable
data class StageDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val slug: String? = null,
    val sections: List<SectionDto> = emptyList(),
)

@Serializable
data class SectionDto(
    val name: String? = null,
    val matches: List<SectionMatchDto> = emptyList(),
    val rankings: List<RankingDto> = emptyList(),
)

@Serializable
data class RankingDto(
    val ordinal: Int? = null,
    val teams: List<StandingsTeamDto> = emptyList(),
)

@Serializable
data class SectionMatchDto(
    val id: String? = null,
    val state: String? = null,
    val teams: List<StandingsTeamDto> = emptyList(),
)

/** Team as sent by getStandings: rankings carry [record], section matches carry [result]. */
@Serializable
data class StandingsTeamDto(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val code: String? = null,
    val image: String? = null,
    val record: RecordDto? = null,
    val result: ResultDto? = null,
)
