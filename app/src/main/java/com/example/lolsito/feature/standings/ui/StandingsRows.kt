package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.feature.schedule.domain.isFavoriteIn
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.SwissRound
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

        /** The row gets the favorite star: its team has one of [favoriteNames], compared as Schedule does. */
        fun isFavorite(favoriteNames: Collection<String>): Boolean = ranking.team.isFavoriteIn(favoriteNames)
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

        /** The team at [index] gets the favorite star, in list rows and bracket cards; false when there is no such team. */
        fun isFavorite(index: Int, favoriteNames: Collection<String>): Boolean =
            sectionMatch.match.teams.getOrNull(index)?.isFavoriteIn(favoriteNames) == true
    }

    /**
     * Bracket section drawn from its official layout: [columns] left to right, and a [lines] entry from each
     * match to the match its winner plays next.
     */
    data class BracketRow(override val key: String, val columns: List<BracketColumn>, val lines: List<BracketLine>) : StandingsRow

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

/** One column of a bracket: its cells top to bottom. */
data class BracketColumn(val cells: List<BracketColumnCell>)

/** A cell of a bracket column as display rows: its official title and its match cards, top to bottom. */
data class BracketColumnCell(val name: String, val matches: List<StandingsRow.MatchRow>)

/** A line from the card with key [fromKey] to the card with key [toKey], which its winner plays next. */
data class BracketLine(val fromKey: String, val toKey: String)

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
 * show the official layout when the section has columns (a Swiss view by round when no match feeds another,
 * see [buildSwiss], otherwise a bracket), else the match list; and a section with neither shows an empty
 * row. Elimination stages (bracket or Swiss) also get a notice, and skip their sections with no matches.
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
                    val swiss = buildSwiss(section)
                    when {
                        swiss != null -> {
                            val swissKey = key("swiss:$sectionBase")
                            rows += StandingsRow.SwissRow(swissKey, swiss.map { round -> swissColumn(round, ::matchRow) })
                        }
                        section.columns.isNotEmpty() -> rows += bracketRow(key("bracket:$sectionBase"), section, ::matchRow)
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

/**
 * A section's official layout as display rows. Every match row is built once; each winner link becomes a
 * line between the cards of its two matches (the first card of a match id, should one repeat).
 */
private fun bracketRow(key: String, section: StandingsSection, matchRow: (SectionMatch) -> StandingsRow.MatchRow): StandingsRow.BracketRow {
    val cardsById = mutableMapOf<String, StandingsRow.MatchRow>()
    val columns = section.columns.map { column ->
        BracketColumn(
            column.cells.map { cell ->
                BracketColumnCell(cell.name, cell.matches.map { match -> matchRow(match).also { cardsById.putIfAbsent(match.match.id, it) } })
            },
        )
    }
    val lines = section.winnerLinks.mapNotNull { link ->
        val from = cardsById[link.fromMatchId] ?: return@mapNotNull null
        val to = cardsById[link.toMatchId] ?: return@mapNotNull null
        BracketLine(from.key, to.key)
    }
    return StandingsRow.BracketRow(key, columns, lines)
}

/** A Swiss round as display rows; every match row is built once and shared by the round and its group. */
private fun swissColumn(round: SwissRound, matchRow: (SectionMatch) -> StandingsRow.MatchRow): SwissColumn {
    val rows = round.matches.associateWith(matchRow)
    val groups = round.groups.map { group ->
        SwissGroupColumn(group.record?.let { formatRecord(it.wins, it.losses) }, group.matches.map { rows.getValue(it) })
    }
    return SwissColumn(round.name, round.matches.map { rows.getValue(it) }, groups)
}
