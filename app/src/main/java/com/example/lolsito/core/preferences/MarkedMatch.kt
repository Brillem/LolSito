package com.example.lolsito.core.preferences

import java.time.DateTimeException
import java.time.Instant

/**
 * A match the user asked to be reminded of ("Remind me"), whatever its teams. It keeps what the reminder sync
 * needs to find it ([leagueId], the schedule filter) and what Settings lists without a request.
 */
data class MarkedMatch(
    val matchId: String,
    val leagueId: String,
    /** The official start when it was marked; the schedule's own start wins once a sync reads it. */
    val startTime: Instant,
    /** The teams by code, e.g. "GEN vs BLG". */
    val teamsLabel: String,
    /** The league and, when known, the format, e.g. "LCK · Bo5". */
    val detail: String,
)

private const val MARKED_FIELD_COUNT = 5

/** Same format as the favorites (see [encodeFavoriteTeams]); the start time is in epoch milliseconds. */
internal fun encodeMarkedMatches(matches: List<MarkedMatch>): String =
    encodeRecords(matches.map { listOf(it.matchId, it.leagueId, it.startTime.toEpochMilli().toString(), it.teamsLabel, it.detail) })

/** Inverse of [encodeMarkedMatches]; a value that does not parse has no marked match at all. */
internal fun decodeMarkedMatches(raw: String): List<MarkedMatch> =
    decodeRecords(raw, MARKED_FIELD_COUNT) { fields ->
        val millis = requireNotNull(fields[2].toLongOrNull()) { "bad start time" }
        val start = try {
            Instant.ofEpochMilli(millis)
        } catch (e: DateTimeException) {
            throw IllegalArgumentException(e)
        }
        MarkedMatch(fields[0], fields[1], start, fields[3], fields[4])
    }.distinctBy { it.matchId }
