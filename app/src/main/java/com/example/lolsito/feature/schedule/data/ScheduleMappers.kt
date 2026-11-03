package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.network.dto.EventDto
import com.example.lolsito.core.network.dto.MatchDto
import com.example.lolsito.core.network.dto.ScheduleDto
import com.example.lolsito.core.network.toMatchState
import com.example.lolsito.core.network.toSeriesFormat
import com.example.lolsito.core.network.toTeam
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import java.time.Instant
import java.time.format.DateTimeParseException

fun ScheduleDto.toSchedulePage(): SchedulePage = SchedulePage(
    events = events.mapNotNull { it.toDomain() },
    olderToken = pages?.older,
    newerToken = pages?.newer,
)

/** Returns null when the event has no parseable start time. */
fun EventDto.toDomain(): ScheduleEvent? {
    val instant = try {
        startTime?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    } ?: return null
    val leagueName = league?.name.orEmpty()
    val mappedMatch = match?.toDomain()
    return ScheduleEvent(
        startTime = instant,
        state = state.toMatchState(),
        blockName = blockName,
        leagueName = leagueName,
        leagueSlug = league?.slug,
        match = mappedMatch,
    )
}

private fun MatchDto.toDomain(): Match? {
    val matchId = id?.takeIf { it.isNotBlank() } ?: return null
    return Match(
        id = matchId,
        teams = teams.orEmpty().map { it.toTeam() },
        format = strategy.toSeriesFormat(),
    )
}
