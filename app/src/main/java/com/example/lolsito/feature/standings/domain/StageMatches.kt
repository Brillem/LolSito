package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.isFavoriteIn
import com.example.lolsito.feature.schedule.domain.mergeEvents
import com.example.lolsito.core.model.ordinalLabel
import java.time.LocalDate
import java.time.ZoneId

/** A stage as the stage selector shows it: the API name and a [key] unique within its standings. */
data class StageTab(val key: String, val name: String)

/** One tab per stage, in API order; the key is the stage slug (or name) with an occurrence ordinal. */
fun stageTabs(standings: Standings): List<StageTab> {
    val unique = UniqueKeys()
    return standings.stages.map { StageTab(unique.next(it.slug ?: it.name), it.name) }
}

/**
 * The stage a tournament opens on, by API order (which is chronological): the first stage with a match
 * being played; otherwise the latest stage that has been played (a completed or live match, or for a
 * ranked stage a team with a record above 0-0); otherwise the first stage. Ranked sections carry no
 * matches in the standings, so their records are the only sign they started.
 */
fun defaultStageIndex(stages: List<StandingsStage>): Int {
    val live = stages.indexOfFirst { stage -> stage.allMatches().any { it.state == MatchState.IN_PROGRESS } }
    if (live >= 0) return live
    val played = stages.indexOfLast { it.hasBeenPlayed() }
    return if (played >= 0) played else 0
}

private fun StandingsStage.allMatches(): List<SectionMatch> = sections.flatMap { it.matches }

private fun StandingsStage.hasBeenPlayed(): Boolean =
    allMatches().any { it.state == MatchState.COMPLETED || it.state == MatchState.IN_PROGRESS } ||
        sections.any { section -> section.rankings.any { (it.wins ?: 0) + (it.losses ?: 0) > 0 } }

/**
 * Whether the stage names fit a segmented button row without truncating: at most three stages, each name
 * short enough for its share of a phone's width ([SEGMENT_NAME_LENGTH_TWO] characters with two segments,
 * [SEGMENT_NAME_LENGTH_THREE] with three). Otherwise the selector is a scrollable row.
 */
fun stageSelectorFitsSegments(names: List<String>): Boolean {
    val limit = when (names.size) {
        in 0..2 -> SEGMENT_NAME_LENGTH_TWO
        3 -> SEGMENT_NAME_LENGTH_THREE
        else -> return false
    }
    return names.all { it.length <= limit }
}

private const val SEGMENT_NAME_LENGTH_TWO = 16
private const val SEGMENT_NAME_LENGTH_THREE = 10

/** A stage with a ranking table: it can also show its matches, which the standings do not carry. */
val StandingsStage.isRanked: Boolean get() = sections.any { it.isRanked }

/** Ids of every match of the elimination (bracket and Swiss) stages. */
fun eliminationMatchIds(standings: Standings): Set<String> =
    standings.stages.filter { it.isElimination }.flatMap { it.allMatches() }.mapTo(HashSet()) { it.match.id }

/**
 * The matches of the ranked stages of [tournament]: the [events] with a match whose start falls within its
 * dates (inclusive, in [zoneId]), except those of an elimination stage of [standings], so playoff matches
 * played inside the same dates stay in their bracket. Chronological. Empty when the tournament has no dates.
 */
fun regularStageMatches(
    events: List<ScheduleEvent>,
    tournament: Tournament,
    standings: Standings,
    zoneId: ZoneId,
): List<ScheduleEvent> {
    val start = tournament.startDate ?: return emptyList()
    val end = tournament.endDate ?: return emptyList()
    val excluded = eliminationMatchIds(standings)
    return events
        .filter { event ->
            val id = event.matchId ?: return@filter false
            val date = event.startTime.atZone(zoneId).toLocalDate()
            id !in excluded && !date.isBefore(start) && !date.isAfter(end)
        }
        .sortedBy { it.startTime }
}

/**
 * The event's match is played by [team]. Schedule teams carry no id, so a team is recognized by name as
 * favorites are ([isFavoriteIn]: trimmed, ignoring case) or, should it have been renamed, by its code.
 */
fun ScheduleEvent.involvesTeam(team: Team): Boolean =
    match?.teams?.any { it.isFavoriteIn(listOf(team.name)) || it.code.equals(team.code, ignoreCase = true) } == true

/** A team the matches can be filtered by, with its [ranking] in the stage and whether it is a favorite. */
data class TeamOption(val team: Team, val ranking: Ranking, val favorite: Boolean)

/**
 * The teams of [stage] in table order (section by section), each once, without "TBD" placeholders; the
 * favorites ([favoriteNames], matched as Schedule does) come first, keeping that order among themselves.
 */
fun stageTeamOptions(stage: StandingsStage, favoriteNames: Collection<String>): List<TeamOption> {
    val options = stage.sections
        .flatMap { it.rankings }
        .filterNot { it.team.isPlaceholder }
        .distinctBy { it.team.identity }
        .map { TeamOption(it.team, it, it.team.isFavoriteIn(favoriteNames)) }
    val (favorites, others) = options.partition { it.favorite }
    return favorites + others
}

/** The ranking of [team] in [stage], by id (or code) first and then by name ignoring case; null when unranked there. */
fun stageRankingOf(stage: StandingsStage, team: Team): Ranking? {
    val rankings = stage.sections.flatMap { it.rankings }
    val name = team.name.trim()
    return rankings.firstOrNull { it.team.identity == team.identity }
        ?: rankings.firstOrNull { it.team.name.trim().equals(name, ignoreCase = true) }
}

/** "2nd · 5-0": the team's place and record in a stage. Each part degrades on its own; null without either. */
fun stageStandingLine(ranking: Ranking): String? {
    val place = ranking.ordinal?.let { ordinalLabel(it) }
    val wins = ranking.wins
    val losses = ranking.losses
    val record = if (wins != null && losses != null) "$wins-$losses" else null
    return listOfNotNull(place, record).joinToString(" · ").ifEmpty { null }
}

/**
 * The schedule events of [leagueId] covering [start]..[end] (local dates in [zoneId]). The schedule opens
 * around today, so it pages older while the loaded events have not gone before [start], and newer while
 * they have not gone past [end]. A direction stops when it has no token or returns an empty page, and the
 * whole load stops after [maxPages] requests, so a range far from today ends with what was reached. The
 * result may hold events outside the range: see [regularStageMatches]. Request failures throw.
 */
suspend fun ScheduleRepository.loadEventsBetween(
    leagueId: String,
    start: LocalDate,
    end: LocalDate,
    zoneId: ZoneId,
    maxPages: Int = MAX_SCHEDULE_PAGES,
): List<ScheduleEvent> {
    val leagues = listOf(leagueId)
    val first = getSchedule(leagues, pageToken = null)
    var requests = 1
    var events = mergeEvents(emptyList(), first.events)
    fun ScheduleEvent.date(): LocalDate = startTime.atZone(zoneId).toLocalDate()

    var older = first.olderToken
    while (older != null && requests < maxPages && (events.isEmpty() || !events.first().date().isBefore(start))) {
        val page = getSchedule(leagues, older)
        requests++
        if (page.events.isEmpty()) break
        events = mergeEvents(events, page.events)
        older = page.olderToken
    }
    var newer = first.newerToken
    while (newer != null && requests < maxPages && (events.isEmpty() || !events.last().date().isAfter(end))) {
        val page = getSchedule(leagues, newer)
        requests++
        if (page.events.isEmpty()) break
        events = mergeEvents(events, page.events)
        newer = page.newerToken
    }
    return events
}

/** Schedule pages one Matches load may request; a split spans a few pages, so this only bounds a runaway. */
const val MAX_SCHEDULE_PAGES = 20
