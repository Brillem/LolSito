package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.model.MatchResultDisplay
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
 * rankings show the match list, and a section with neither shows an empty row. Stages where every
 * section is match-only (bracket or Swiss) also get an elimination notice.
 */
fun buildStandingsRows(standings: Standings): List<StandingsRow> {
    val rows = mutableListOf<Pair<String, (String) -> StandingsRow>>()
    standings.stages.forEach { stage ->
        val stageBase = "stage:${stage.slug ?: stage.name}"
        rows += stageBase to { key -> StandingsRow.StageHeader(key, stage.name, stage.isElimination) }
        if (stage.isElimination) {
            rows += "notice:$stageBase" to { key -> StandingsRow.EliminationNotice(key) }
        }
        if (stage.sections.isEmpty()) {
            rows += "empty:$stageBase" to { key -> StandingsRow.EmptySection(key) }
        }
        stage.sections.forEach { section ->
            val sectionBase = "$stageBase/${section.name}"
            if (stage.showsSectionHeaders()) {
                rows += "section:$sectionBase" to { key -> StandingsRow.SectionHeader(key, section.name) }
            }
            when {
                section.isRanked -> {
                    rows += "table:$sectionBase" to { key -> StandingsRow.TableHeader(key) }
                    section.rankings.forEach { ranking ->
                        val teamId = ranking.team.id ?: ranking.team.code
                        rows += "rank:$sectionBase/$teamId" to { key -> StandingsRow.RankingRow(key, ranking) }
                    }
                }
                section.isMatchOnly -> section.matches.forEach { match ->
                    rows += "match:$sectionBase/${match.match.id}" to { key -> StandingsRow.MatchRow(key, match) }
                }
                else -> rows += "empty:$sectionBase" to { key -> StandingsRow.EmptySection(key) }
            }
        }
    }
    // Same "#ordinal" suffix pattern as ScheduleDay.keys, applied to the whole list.
    val seen = mutableMapOf<String, Int>()
    return rows.map { (base, build) ->
        val ordinal = seen.getOrDefault(base, 0)
        seen[base] = ordinal + 1
        build("$base#$ordinal")
    }
}

/** A single section named like its stage adds nothing, so its header is skipped. */
private fun StandingsStage.showsSectionHeaders(): Boolean =
    sections.size > 1 || sections.any { it.name.isNotBlank() && it.name != name }
