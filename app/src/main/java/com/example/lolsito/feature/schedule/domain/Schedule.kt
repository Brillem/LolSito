package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
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
) {
    /** The only real identity an event has; null for match-less events (e.g. show matches). */
    val matchId: String? get() = match?.id
}

data class SchedulePage(
    val events: List<ScheduleEvent>,
    val olderToken: String?,
    val newerToken: String?,
)

data class ScheduleDay(val date: LocalDate, val events: List<ScheduleEvent>) {
    /**
     * LazyColumn keys aligned with [events]. Match events use their match id; match-less events
     * use time/league/block plus an occurrence ordinal, so keys never collide within a list.
     */
    val keys: List<String> by lazy {
        val seen = mutableMapOf<String, Int>()
        events.map { event ->
            event.matchId?.let { "match:$it" } ?: run {
                val base = "event:${event.startTime}|${event.leagueSlug ?: event.leagueName}|${event.blockName.orEmpty()}"
                val ordinal = seen.getOrDefault(base, 0)
                seen[base] = ordinal + 1
                "$base#$ordinal"
            }
        }
    }
}

interface ScheduleRepository {
    suspend fun getSchedule(leagueIds: List<String>, pageToken: String?): SchedulePage
}

fun groupEventsByDay(events: List<ScheduleEvent>, zoneId: ZoneId): List<ScheduleDay> =
    events.sortedBy { it.startTime }
        .groupBy { it.startTime.atZone(zoneId).toLocalDate() }
        .map { (date, dayEvents) -> ScheduleDay(date, dayEvents) }

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
