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

    /** Ranked stage section that has no rankings yet. */
    data class EmptySection(override val key: String) : StandingsRow

    /** Bracket/Swiss stage. Without [hasMatches] the UI points to the Schedule tab instead. */
    data class EliminationNotice(override val key: String, val hasMatches: Boolean) : StandingsRow

    data class MatchRow(override val key: String, val sectionMatch: SectionMatch) : StandingsRow {
        val result: MatchResultDisplay
            get() = formatMatchResult(sectionMatch.state, sectionMatch.match.teams, sectionMatch.match.bestOf)
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
 * Stages in API order. Ranked stages show a table per section; stages without any rankings
 * (bracket or Swiss) show an elimination notice followed by their section matches.
 */
fun buildStandingsRows(standings: Standings): List<StandingsRow> {
    val rows = mutableListOf<Pair<String, (String) -> StandingsRow>>()
    standings.stages.forEach { stage ->
        val stageBase = "stage:${stage.slug ?: stage.name}"
        rows += stageBase to { key -> StandingsRow.StageHeader(key, stage.name, stage.isElimination) }
        if (stage.isElimination) {
            val hasMatches = stage.sections.any { it.matches.isNotEmpty() }
            rows += "notice:$stageBase" to { key -> StandingsRow.EliminationNotice(key, hasMatches) }
        }
        stage.sections.forEach { section ->
            val sectionBase = "$stageBase/${section.name}"
            if (stage.isElimination && section.matches.isEmpty()) return@forEach
            if (stage.showsSectionHeaders()) {
                rows += "section:$sectionBase" to { key -> StandingsRow.SectionHeader(key, section.name) }
            }
            when {
                stage.isElimination -> section.matches.forEach { match ->
                    rows += "match:$sectionBase/${match.match.id}" to { key -> StandingsRow.MatchRow(key, match) }
                }
                section.rankings.isEmpty() ->
                    rows += "empty:$sectionBase" to { key -> StandingsRow.EmptySection(key) }
                else -> {
                    rows += "table:$sectionBase" to { key -> StandingsRow.TableHeader(key) }
                    section.rankings.forEach { ranking ->
                        val teamId = ranking.team.id ?: ranking.team.code
                        rows += "rank:$sectionBase/$teamId" to { key -> StandingsRow.RankingRow(key, ranking) }
                    }
                }
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
