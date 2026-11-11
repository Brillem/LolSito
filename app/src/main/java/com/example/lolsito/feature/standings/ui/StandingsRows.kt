package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.MatchResultDisplay
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsStage

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
    }

    /** Section (or section-less stage) with neither rankings nor matches yet. */
    data class EmptySection(override val key: String) : StandingsRow

    /** Bracket/Swiss stage: every section lists matches instead of a table. */
    data class EliminationNotice(override val key: String) : StandingsRow

    data class MatchRow(override val key: String, val sectionMatch: SectionMatch) : StandingsRow {
        val result: MatchResultDisplay
            get() = formatMatchResult(sectionMatch.state, sectionMatch.match.teams, sectionMatch.match.format)

        /** Both teams are known, so the row shows them; otherwise it reads "TBD". */
        val hasBothTeams: Boolean get() = sectionMatch.match.teams.size >= 2

        /** An undecided match (TBD) has no detail worth opening. */
        val isNavigable: Boolean get() = hasBothTeams
    }
}

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
 * Stages in API order. Each section decides on its own: rankings show a table, matches without
 * rankings show the match list, and a section with neither shows an empty row. Elimination stages
 * (bracket or Swiss) also get a notice, and skip their rounds that are not scheduled yet.
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
                section.isMatchOnly -> section.matches.forEach { match ->
                    rows += StandingsRow.MatchRow(key("match:$sectionBase/${match.match.id}"), match)
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
