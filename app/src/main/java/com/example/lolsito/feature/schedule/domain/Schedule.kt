package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.UniqueKeys
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleEvent(
    val startTime: Instant,
    val state: MatchState,
    val blockName: String?,
    val leagueName: String,
    val leagueSlug: String?,
    val match: Match?,
    /** League id; only the live feed sends it (the schedule does not). */
    val leagueId: String? = null,
) {
    /** The only real identity an event has; null for match-less events (e.g. show matches). */
    val matchId: String? get() = match?.id
}

data class SchedulePage(
    val events: List<ScheduleEvent>,
    val olderToken: String?,
    val newerToken: String?,
)

/**
 * One day of a schedule section. [keyPrefix] names the section, so the keys of two sections that
 * share a date or hold look-alike match-less events never collide in one list.
 */
data class ScheduleDay(val date: LocalDate, val events: List<ScheduleEvent>, val keyPrefix: String = "") {
    /** LazyColumn key of the day header. */
    val headerKey: String get() = "${keyPrefix}day-$date"

    /**
     * LazyColumn keys aligned with [events]. Match events use their match id; match-less events
     * use time/league/block plus an occurrence ordinal, so keys never collide within a list.
     */
    val keys: List<String> by lazy {
        val unique = UniqueKeys()
        events.map { event ->
            keyPrefix + (event.matchId?.let { "match:$it" }
                ?: unique.next("event:${event.startTime}|${event.leagueSlug ?: event.leagueName}|${event.blockName.orEmpty()}"))
        }
    }
}

interface ScheduleRepository {
    suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage
}

/** Matches being played right now, across all leagues. */
interface LiveMatchesRepository {
    /** Live events that are matches, in feed order; empty when nothing is live. */
    suspend fun getLiveMatches(): List<ScheduleEvent>
}

/** Days and the events inside them run oldest first, or newest first when [newestFirst]. */
fun groupEventsByDay(
    events: List<ScheduleEvent>,
    zoneId: ZoneId,
    newestFirst: Boolean = false,
    keyPrefix: String = "",
): List<ScheduleDay> =
    (if (newestFirst) events.sortedByDescending { it.startTime } else events.sortedBy { it.startTime })
        .groupBy { it.startTime.atZone(zoneId).toLocalDate() }
        .map { (date, dayEvents) -> ScheduleDay(date, dayEvents, keyPrefix) }

/**
 * What the schedule screen shows, top to bottom: [live] matches, [upcoming] days (soonest first) and
 * [results] days (most recent first). A match is in exactly one of them.
 */
data class ScheduleSections(
    val live: List<ScheduleEvent>,
    val upcoming: List<ScheduleDay>,
    val results: List<ScheduleDay>,
)

/**
 * Splits [events] into the three sections.
 *
 * [liveFeed] is every match the live feed reports (any league), or null while it has not answered.
 * Once it answered it alone decides what is live: the live section is its matches of [liveLeagueIds]
 * (all leagues when null), earliest start first, and a match it lists is removed from the day lists,
 * wherever the schedule says it is. Until it answers, an event whose own state is in progress is live.
 * Of the remaining events only an unstarted one is upcoming; the rest (completed, unknown, or in
 * progress but absent from the feed, i.e. already over) are results.
 */
fun buildScheduleSections(
    events: List<ScheduleEvent>,
    liveFeed: List<ScheduleEvent>?,
    liveLeagueIds: Set<String>?,
    zoneId: ZoneId,
): ScheduleSections {
    val live = (liveFeed?.filter { liveLeagueIds == null || it.leagueId in liveLeagueIds }
        ?: events.filter { it.state == MatchState.IN_PROGRESS }).sortedBy { it.startTime }
    val liveIds = live.mapNotNullTo(HashSet()) { it.matchId }
    val (upcoming, results) = events
        .filterNot { event -> event.matchId?.let { it in liveIds } ?: (event in live) }
        .partition { it.state == MatchState.UNSTARTED }
    return ScheduleSections(
        live = live,
        upcoming = groupEventsByDay(upcoming, zoneId, keyPrefix = "upcoming:"),
        results = groupEventsByDay(results, zoneId, newestFirst = true, keyPrefix = "results:"),
    )
}

/** "a - b" from the current game wins of a two-team match, or null when they are not both known. */
fun liveScoreLabel(teams: List<Team>): String? {
    if (teams.size < 2) return null
    val first = teams[0].gameWins ?: return null
    val second = teams[1].gameWins ?: return null
    return "$first - $second"
}

/**
 * Events with a match are deduplicated by match id, and an incoming copy replaces the existing
 * one because it is fresher; match-less events only collapse when fully equal. The result is
 * sorted chronologically.
 */
fun mergeEvents(existing: List<ScheduleEvent>, incoming: List<ScheduleEvent>): List<ScheduleEvent> {
    val incomingIds = incoming.mapNotNullTo(HashSet()) { it.matchId }
    val kept = existing.filter { it.matchId == null || it.matchId !in incomingIds }
    return (kept + incoming).distinctBy { it.matchId ?: it }.sortedBy { it.startTime }
}
