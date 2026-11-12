package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.BracketRound
import com.example.lolsito.feature.standings.domain.SwissRound
import com.example.lolsito.feature.standings.domain.buildBracket
import com.example.lolsito.feature.standings.domain.buildDoubleElimination
import com.example.lolsito.feature.standings.domain.buildSwiss
import com.example.lolsito.feature.standings.domain.isPlaceholder

/** Flat, display-ready rows of a standings list. Every [key] is unique within one list. */
sealed interface StandingsRow {
    val key: String

    data class StageHeader(override val key: String, val name: String, val isElimination: Boolean) : StandingsRow

    data class SectionHeader(override val key: String, val name: String) : StandingsRow

    data class TableHeader(override val key: String) : StandingsRow

    data class RankingRow(override val key: String, val ranking: Ranking) : StandingsRow {
        /** Ordinal as text; tied teams share it, "-" when the API sent none. */
        val position: String get() = ranking.ordinal?.toString() ?: "-"
        val record: String get() = formatRecord(ranking.wins, ranking.losses)

        /** Id of the team profile this row opens; null (not tappable) without an id or for a "TBD" placeholder. */
        val teamId: String? get() = ranking.team.id?.takeUnless { ranking.team.isPlaceholder }
    }

    /** Section (or section-less stage) with neither rankings nor matches yet. */
    data class EmptySection(override val key: String) : StandingsRow

    /** Bracket/Swiss stage: every section lists matches instead of a table. */
    data class EliminationNotice(override val key: String) : StandingsRow

    data class MatchRow(override val key: String, val sectionMatch: SectionMatch) : StandingsRow {
        val result: MatchResultDisplay
            get() = formatMatchResult(sectionMatch.state, sectionMatch.match.teams, sectionMatch.match.format)

        /** [result], or with [noSpoilers] on, that of a finished match without its score and winner. */
        fun displayedResult(noSpoilers: Boolean): MatchResultDisplay =
            if (noSpoilers && sectionMatch.state == MatchState.COMPLETED) result.copy(score = null, winnerIndex = null) else result

        /** Both teams are known, so the row shows them; otherwise it reads "TBD". */
        val hasBothTeams: Boolean get() = sectionMatch.match.teams.size >= 2

        /** An undecided match (TBD) has no detail worth opening. */
        val isNavigable: Boolean get() = hasBothTeams

        /** Being played right now: list rows and bracket cards show "LIVE". */
        val isLive: Boolean get() = sectionMatch.state == MatchState.IN_PROGRESS

        /** Game wins of the team at [index] as text, only when [displayedResult] shows a score. */
        fun displayedGameWins(index: Int, noSpoilers: Boolean): String? =
            displayedResult(noSpoilers).score?.let { sectionMatch.match.teams.getOrNull(index)?.gameWins?.toString() }
    }

    /** Single-elimination section drawn as a bracket instead of a match list; see [buildBracket]. */
    data class BracketRow(override val key: String, val rounds: List<BracketColumn>) : StandingsRow

    /** Double-elimination section: upper bracket, lower bracket and grand final; see [buildDoubleElimination]. */
    data class DoubleEliminationRow(
        override val key: String,
        val upper: List<BracketColumn>,
        val lower: List<BracketColumn>,
        val grandFinal: BracketColumn,
    ) : StandingsRow

    /** Swiss section drawn by round, each round grouped by record; see [buildSwiss]. */
    data class SwissRow(override val key: String, val rounds: List<SwissColumn>) : StandingsRow
}

/** One Swiss round: its matches in list order, and the same matches grouped by the teams' record. */
data class SwissColumn(val name: String, val matches: List<StandingsRow.MatchRow>, val groups: List<SwissGroupColumn>) {
    /**
     * [groups], or with [noSpoilers] on, a single group with every match in list order: records are results,
     * and so is the order they would put the matches in.
     */
    fun displayedGroups(noSpoilers: Boolean): List<SwissGroupColumn> =
        if (noSpoilers) listOf(SwissGroupColumn(record = null, matches = matches)) else groups
}

/** Matches of a Swiss round under one record such as "1-0"; null for matches still to be paired or no grouping. */
data class SwissGroupColumn(val record: String?, val matches: List<StandingsRow.MatchRow>)

/**
 * One bracket round as display rows: its name and its matches, top to bottom. [linkedToNext] is false when the
 * matches it feeds in the next column are not known yet, so no connector lines are drawn.
 */
data class BracketColumn(val name: String, val matches: List<StandingsRow.MatchRow>, val linkedToNext: Boolean = true)

/**
 * Win-loss record as text: "5-3" when complete, "-" when missing. A record with only one value
 * shows "-" for the missing one, spaced so it does not read as a minus sign: "3 - -", "- - 2".
 */
fun formatRecord(wins: Int?, losses: Int?): String = when {
    wins != null && losses != null -> "$wins-$losses"
    wins == null && losses == null -> "-"
    else -> "${wins ?: "-"} - ${losses ?: "-"}"
}

/**
 * Stages in API order. Each section decides on its own: rankings show a table; matches without rankings
 * show a single-elimination bracket (see [buildBracket]), else a double-elimination one (see
 * [buildDoubleElimination]), else a Swiss view (see [buildSwiss]), else the match list; and a section with
 * neither shows an empty row. Elimination stages (bracket or Swiss) also get a notice, and skip their
 * rounds that are not scheduled yet.
 */
fun buildStandingsRows(standings: Standings): List<StandingsRow> {
    // Same "#ordinal" suffix as ScheduleDay.keys, applied to the whole list.
    val unique = UniqueKeys()
    fun key(base: String) = unique.next(base)
    val rows = mutableListOf<StandingsRow>()
    standings.stages.forEach { stage ->
        val stageBase = "stage:${stage.slug ?: stage.name}"
        rows += StandingsRow.StageHeader(key(stageBase), stage.name, stage.isElimination)
        if (stage.isElimination) {
            rows += StandingsRow.EliminationNotice(key("notice:$stageBase"))
        }
        if (stage.sections.isEmpty()) {
            rows += StandingsRow.EmptySection(key("empty:$stageBase"))
        }
        val sections = if (stage.isElimination) stage.sections.filter { it.isMatchOnly } else stage.sections
        sections.forEach { section ->
            val sectionBase = "$stageBase/${section.name}"
            if (stage.showsSectionHeaders()) {
                rows += StandingsRow.SectionHeader(key("section:$sectionBase"), section.name)
            }
            when {
                section.isRanked -> {
                    rows += StandingsRow.TableHeader(key("table:$sectionBase"))
                    section.rankings.forEach { ranking ->
                        val teamId = ranking.team.id ?: ranking.team.code
                        rows += StandingsRow.RankingRow(key("rank:$sectionBase/$teamId"), ranking)
                    }
                }
                section.isMatchOnly -> {
                    fun matchRow(match: SectionMatch) = StandingsRow.MatchRow(key("match:$sectionBase/${match.match.id}"), match)
                    fun column(round: BracketRound) = BracketColumn(round.name, round.matches.map(::matchRow), round.linkedToNext)
                    val bracket = buildBracket(section)
                    val doubleElimination = if (bracket == null) buildDoubleElimination(section) else null
                    val swiss = if (bracket == null && doubleElimination == null) buildSwiss(section) else null
                    when {
                        bracket != null -> {
                            val bracketKey = key("bracket:$sectionBase")
                            rows += StandingsRow.BracketRow(bracketKey, bracket.map(::column))
                        }
                        doubleElimination != null -> {
                            val bracketKey = key("double:$sectionBase")
                            rows += StandingsRow.DoubleEliminationRow(
                                bracketKey,
                                doubleElimination.upper.map(::column),
                                doubleElimination.lower.map(::column),
                                column(doubleElimination.grandFinal),
                            )
                        }
                        swiss != null -> {
                            val swissKey = key("swiss:$sectionBase")
                            rows += StandingsRow.SwissRow(swissKey, swiss.map { round -> swissColumn(round, ::matchRow) })
                        }
                        else -> section.matches.forEach { rows += matchRow(it) }
                    }
                }
                else -> rows += StandingsRow.EmptySection(key("empty:$sectionBase"))
            }
        }
    }
    return rows
}

/** A single section named like its stage adds nothing, so its header is skipped. */
private fun StandingsStage.showsSectionHeaders(): Boolean =
    sections.size > 1 || sections.any { it.name.isNotBlank() && it.name != name }

/** A Swiss round as display rows; every match row is built once and shared by the round and its group. */
private fun swissColumn(round: SwissRound, matchRow: (SectionMatch) -> StandingsRow.MatchRow): SwissColumn {
    val rows = round.matches.associateWith(matchRow)
    val groups = round.groups.map { group ->
        SwissGroupColumn(group.record?.let { formatRecord(it.wins, it.losses) }, group.matches.map { rows.getValue(it) })
    }
    return SwissColumn(round.name, round.matches.map { rows.getValue(it) }, groups)
}
