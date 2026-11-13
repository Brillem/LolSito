package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.network.dto.RankingDto
import com.example.lolsito.core.network.dto.SectionDto
import com.example.lolsito.core.network.dto.SectionMatchDto
import com.example.lolsito.core.network.dto.StageDto
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.core.network.dto.TournamentDto
import com.example.lolsito.core.network.dto.TournamentsResponse
import com.example.lolsito.core.network.toMatchState
import com.example.lolsito.core.network.toTeam
import com.example.lolsito.feature.standings.domain.BracketCell
import com.example.lolsito.feature.standings.domain.BracketLayoutColumn
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.WinnerLink
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

/**
 * A section with its official layout: the matches of [SectionDto.columns] in column then cell order, or the
 * flat [SectionDto.matches] list when the section has no columns. [WinnerLink]s come from the team origins
 * that name another match of the section with slot 1 (its winner); slot 2 (its loser) is a drop to the lower
 * bracket and gets no link.
 */
private fun SectionDto.toDomain(): StandingsSection {
    val layout = columns.map { column ->
        column.cells.map { cell -> cell.name.orEmpty() to cell.matches.mapNotNull { dto -> dto.toDomain()?.let { dto to it } } }
    }
    val laidOut = layout.flatten().flatMap { (_, matches) -> matches }
    val idsByStructuralId = laidOut.mapNotNull { (dto, match) -> dto.structuralId?.let { it to match.match.id } }.toMap()
    val winnerLinks = laidOut.flatMap { (dto, match) ->
        dto.teams.mapNotNull { team ->
            val origin = team.origin?.takeIf { it.type == ORIGIN_MATCH && it.slot == WINNER_SLOT } ?: return@mapNotNull null
            idsByStructuralId[origin.structuralId]?.let { WinnerLink(fromMatchId = it, toMatchId = match.match.id) }
        }
    }
    return StandingsSection(
        name = name.orEmpty(),
        rankings = rankings.flatMap { it.toDomain() }.sortedWith(compareBy(nullsLast()) { it.ordinal }),
        matches = if (layout.isEmpty()) matches.mapNotNull { it.toDomain() } else laidOut.map { it.second },
        columns = layout.map { cells -> BracketLayoutColumn(cells.map { (cellName, matches) -> BracketCell(cellName, matches.map { it.second }) }) },
        winnerLinks = winnerLinks,
    )
}

/** Origin type of a slot whose team comes from another match of the section. */
private const val ORIGIN_MATCH = "match"

/** Slot of a match origin that holds the winner; slot 2 holds the loser. */
private const val WINNER_SLOT = 1

/** One ranking slot may hold several tied teams; each becomes a row with the shared ordinal. */
private fun RankingDto.toDomain(): List<Ranking> {
    return teams.map { team ->
        Ranking(
            ordinal = ordinal,
            team = team.toTeam(),
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
        // getStandingsV3 sends no best-of strategy.
        match = Match(id = matchId, teams = teams.map { it.toTeam() }, format = null),
    )
}
