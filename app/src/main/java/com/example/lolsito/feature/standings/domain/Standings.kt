package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import java.time.LocalDate

/** A league's tournament (e.g. a split or a Worlds edition). Dates are null when unparseable. */
data class Tournament(
    val id: String,
    val slug: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)

/**
 * One team's row in a section table. Tied teams share the same [ordinal]. [ordinal], [wins] and
 * [losses] are null when the API did not send them.
 */
data class Ranking(
    val ordinal: Int?,
    val team: Team,
    val wins: Int?,
    val losses: Int?,
)

/** A match of a bracket or Swiss section. */
data class SectionMatch(
    val state: MatchState,
    val match: Match,
)

/** A named group of matches inside a bracket column, as the API titles it, e.g. "Lower Bracket - Finals". */
data class BracketCell(val name: String, val matches: List<SectionMatch>)

/** One column of the official bracket layout: its cells top to bottom. */
data class BracketLayoutColumn(val cells: List<BracketCell>)

/** The winner of match [fromMatchId] plays match [toMatchId]. Drops to a lower bracket are not links. */
data class WinnerLink(val fromMatchId: String, val toMatchId: String)

/**
 * A ranked section shows a table; a match-only one (bracket, Swiss) shows its matches instead.
 *
 * [columns] is the official layout of a bracket section, left to right, and [winnerLinks] where each winner
 * goes next; [matches] holds every match of the section, those of [columns] in column then cell order. A
 * match-only section without [columns] shows [matches] as a list.
 */
data class StandingsSection(
    val name: String,
    val rankings: List<Ranking>,
    val matches: List<SectionMatch>,
    val columns: List<BracketLayoutColumn> = emptyList(),
    val winnerLinks: List<WinnerLink> = emptyList(),
) {
    val isRanked: Boolean get() = rankings.isNotEmpty()
    val isMatchOnly: Boolean get() = rankings.isEmpty() && matches.isNotEmpty()
}

data class StandingsStage(
    val name: String,
    val slug: String?,
    val sections: List<StandingsSection>,
) {
    /**
     * Bracket and Swiss stages: no section has rankings and at least one lists matches; the
     * remaining sections are rounds not scheduled yet. A stage with no sections, or whose sections
     * all have neither (e.g. a round robin not started yet), is not.
     */
    val isElimination: Boolean get() = sections.none { it.isRanked } && sections.any { it.isMatchOnly }
}

data class Standings(val stages: List<StandingsStage>)

interface StandingsRepository {
    /** Tournaments of a league, newest first (API order). */
    suspend fun getTournaments(leagueId: String): List<Tournament>
    suspend fun getStandings(tournamentId: String): Standings
}

/**
 * Picks the tournament to show by default: the one whose date range (inclusive) contains [today],
 * otherwise the most recent one that has already started, otherwise the first one.
 */
fun pickDefaultTournament(tournaments: List<Tournament>, today: LocalDate): Tournament? {
    val ongoing = tournaments.firstOrNull { t ->
        val start = t.startDate
        val end = t.endDate
        start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
    }
    if (ongoing != null) return ongoing
    val started = tournaments
        .filter { t -> t.startDate?.let { !it.isAfter(today) } == true }
        .maxByOrNull { requireNotNull(it.startDate) }
    return started ?: tournaments.firstOrNull()
}
