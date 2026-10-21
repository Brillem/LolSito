package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.dto.EventDto
import com.example.lolsito.core.network.dto.LeagueDto
import com.example.lolsito.core.network.dto.MatchDto
import com.example.lolsito.core.network.dto.ScheduleDto
import com.example.lolsito.core.network.dto.TeamDto
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.SchedulePage
import java.time.Instant
import java.time.format.DateTimeParseException

fun String?.toHttps(): String? = this?.replaceFirst(Regex("^http://"), "https://")

fun String?.toMatchState(): MatchState = when (this) {
    "unstarted" -> MatchState.UNSTARTED
    "inProgress" -> MatchState.IN_PROGRESS
    "completed" -> MatchState.COMPLETED
    else -> MatchState.UNKNOWN
}

private fun String?.toOutcome(): MatchOutcome? = when (this) {
    "win" -> MatchOutcome.WIN
    "loss" -> MatchOutcome.LOSS
    else -> null
}

private val statusRank = mapOf("force_selected" to 0, "selected" to 1, "not_selected" to 2)

/** Drops hidden leagues and sorts by display status, position, priority, then name. */
fun List<LeagueDto>.toDomainLeagues(): List<League> =
    filter { it.id.isNotBlank() && it.displayPriority?.status != "hidden" }
        .sortedWith(
            compareBy<LeagueDto> { statusRank[it.displayPriority?.status] ?: Int.MAX_VALUE }
                .thenBy { it.displayPriority?.position ?: Int.MAX_VALUE }
                .thenBy { it.priority ?: Int.MAX_VALUE }
                .thenBy { it.name }
        )
        .map { League(it.id, it.slug, it.name, it.region, it.image.toHttps()) }

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
        teams = teams.orEmpty().map { it.toDomain() },
        bestOf = strategy?.count,
    )
}

private fun TeamDto.toDomain(): Team = Team(
    name = name ?: "TBD",
    code = code?.takeIf { it.isNotBlank() } ?: "TBD",
    imageUrl = image.toHttps(),
    outcome = result?.outcome.toOutcome(),
    gameWins = result?.gameWins,
)
