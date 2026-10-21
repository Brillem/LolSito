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

/** A match listed under a section; used as the fallback for bracket and Swiss stages. */
data class SectionMatch(
    val state: MatchState,
    val match: Match,
)

/** A ranked section shows a table; a match-only one (bracket, Swiss) lists its matches instead. */
data class StandingsSection(
    val name: String,
    val rankings: List<Ranking>,
    val matches: List<SectionMatch>,
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
