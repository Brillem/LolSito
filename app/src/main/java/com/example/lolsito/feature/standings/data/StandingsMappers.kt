package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.dto.RankingDto
import com.example.lolsito.core.network.dto.SectionDto
import com.example.lolsito.core.network.dto.SectionMatchDto
import com.example.lolsito.core.network.dto.StageDto
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.core.network.dto.StandingsTeamDto
import com.example.lolsito.core.network.dto.TournamentDto
import com.example.lolsito.core.network.dto.TournamentsResponse
import com.example.lolsito.core.network.toHttps
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun TournamentsResponse.toDomainTournaments(): List<Tournament> =
    data.leagues.flatMap { it.tournaments }.mapNotNull { it.toDomain() }

private fun TournamentDto.toDomain(): Tournament? {
    val tournamentId = id?.takeIf { it.isNotBlank() } ?: return null
    return Tournament(
        id = tournamentId,
        slug = slug.orEmpty(),
        startDate = startDate.toLocalDateOrNull(),
        endDate = endDate.toLocalDateOrNull(),
    )
}

private fun String?.toLocalDateOrNull(): LocalDate? = try {
    this?.let(LocalDate::parse)
} catch (_: DateTimeParseException) {
    null
}

/** Stages of every standings entry, in API order. */
fun StandingsResponse.toDomainStandings(): Standings =
    Standings(stages = data.standings.flatMap { it.stages }.map { it.toDomain() })

private fun StageDto.toDomain(): StandingsStage = StandingsStage(
    name = name.orEmpty(),
    slug = slug,
    sections = sections.map { it.toDomain() },
)

private fun SectionDto.toDomain(): StandingsSection = StandingsSection(
    name = name.orEmpty(),
    rankings = rankings.flatMap { it.toDomain() }.sortedWith(compareBy(nullsLast()) { it.ordinal }),
    matches = matches.mapNotNull { it.toDomain() },
)

/** One ranking slot may hold several tied teams; each becomes a row with the shared ordinal. */
private fun RankingDto.toDomain(): List<Ranking> {
    return teams.map { team ->
        Ranking(
            ordinal = ordinal,
            team = team.toDomain(),
            // Unknown stays null so the UI never shows a missing record as a real 0-0.
            wins = team.record?.wins,
            losses = team.record?.losses,
        )
    }
}

private fun SectionMatchDto.toDomain(): SectionMatch? {
    val matchId = id?.takeIf { it.isNotBlank() } ?: return null
    return SectionMatch(
        state = state.toMatchState(),
        // getStandings sends no best-of strategy.
        match = Match(id = matchId, teams = teams.map { it.toDomain() }, format = null),
    )
}

private fun StandingsTeamDto.toDomain(): Team = Team(
    name = name ?: "TBD",
    code = code?.takeIf { it.isNotBlank() } ?: "TBD",
    imageUrl = image.toHttps(),
    outcome = result?.outcome.toOutcome(),
    gameWins = result?.gameWins,
    id = id?.takeIf { it.isNotBlank() },
)

private fun String?.toMatchState(): MatchState = when (this) {
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
