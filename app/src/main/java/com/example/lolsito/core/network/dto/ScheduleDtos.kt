package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(val data: ScheduleData = ScheduleData())

@Serializable
data class ScheduleData(val schedule: ScheduleDto = ScheduleDto())

@Serializable
data class ScheduleDto(
    val pages: PagesDto? = null,
    val events: List<EventDto> = emptyList(),
)

@Serializable
data class PagesDto(val older: String? = null, val newer: String? = null)

@Serializable
data class EventDto(
    val startTime: String? = null,
    val state: String? = null,
    val type: String? = null,
    val blockName: String? = null,
    val league: EventLeagueDto? = null,
    val match: MatchDto? = null,
)

@Serializable
data class EventLeagueDto(val name: String? = null, val slug: String? = null)

@Serializable
data class MatchDto(
    val id: String? = null,
    val flags: List<String>? = null,
    val strategy: StrategyDto? = null,
    val teams: List<TeamDto>? = null,
)

@Serializable
data class StrategyDto(val type: String? = null, val count: Int? = null)

@Serializable
data class TeamDto(
    val name: String? = null,
    val code: String? = null,
    val image: String? = null,
    val result: ResultDto? = null,
    val record: RecordDto? = null,
)

@Serializable
data class ResultDto(val outcome: String? = null, val gameWins: Int? = null)

@Serializable
data class RecordDto(val wins: Int? = null, val losses: Int? = null)
