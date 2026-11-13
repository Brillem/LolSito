package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentsResponse(val data: TournamentsData = TournamentsData())

@Serializable
data class TournamentsData(val leagues: List<LeagueTournamentsDto> = emptyList())

@Serializable
data class LeagueTournamentsDto(val tournaments: List<TournamentDto> = emptyList())

@Serializable
data class TournamentDto(
    val id: String? = null,
    val slug: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

/** Answer of getStandingsV3. */
@Serializable
data class StandingsResponse(val data: StandingsData = StandingsData())

@Serializable
data class StandingsData(val standings: List<StandingDto> = emptyList())

@Serializable
data class StandingDto(val stages: List<StageDto> = emptyList())

@Serializable
data class StageDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val slug: String? = null,
    val sections: List<SectionDto> = emptyList(),
)

/**
 * A section of a stage. [type] is `group` (a table: [rankings]) or `bracket` (its matches laid out in
 * [columns]); other values exist, such as `crossGroup`, which also carries rankings. getStandingsV3 sends
 * no flat [matches] list; it is still read so a section that sends one without columns shows it as a list.
 */
@Serializable
data class SectionDto(
    val name: String? = null,
    val type: String? = null,
    val columns: List<ColumnDto> = emptyList(),
    val matches: List<SectionMatchDto> = emptyList(),
    val rankings: List<RankingDto> = emptyList(),
)

/** One column of a bracket section, top to bottom: e.g. "Upper Bracket - Round 2" above "Lower Bracket - Round 1". */
@Serializable
data class ColumnDto(val cells: List<CellDto> = emptyList())

/** A named group of matches inside a column, such as "Quarterfinals" or "Lower Bracket - Finals". */
@Serializable
data class CellDto(
    val name: String? = null,
    val slug: String? = null,
    val matches: List<SectionMatchDto> = emptyList(),
)

@Serializable
data class RankingDto(
    val ordinal: Int? = null,
    val teams: List<StandingsTeamDto> = emptyList(),
)

/** [structuralId] identifies the match inside the bracket layout; team origins point to it. */
@Serializable
data class SectionMatchDto(
    val id: String? = null,
    val structuralId: String? = null,
    val state: String? = null,
    val teams: List<StandingsTeamDto> = emptyList(),
)

/**
 * Team as sent by getStandingsV3: rankings carry [record]; bracket matches carry [result] and [origin],
 * where the slot's team comes from (also for a slot whose team is not known yet).
 */
@Serializable
data class StandingsTeamDto(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val code: String? = null,
    val image: String? = null,
    val record: RecordDto? = null,
    val result: ResultDto? = null,
    val origin: OriginDto? = null,
)

/**
 * Where a match slot's team comes from. With [type] `match`, [structuralId] is another match of the same
 * section and [slot] says which of its teams: 1 is the winner and 2 the loser (checked against every
 * decided match of the tournaments sampled on 2026-11-12, see docs/samples/README.md). Other types (`decisionPoint`, `seeding`) are draws,
 * seeds or an earlier stage, and [slot] is then a position in them.
 */
@Serializable
data class OriginDto(
    val structuralId: String? = null,
    val type: String? = null,
    val slot: Int? = null,
)
