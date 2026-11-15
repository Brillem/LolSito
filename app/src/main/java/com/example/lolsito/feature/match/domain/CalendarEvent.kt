package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import java.time.Duration
import java.time.Instant

/** What the phone's calendar is prefilled with for a series still to be played. */
data class CalendarEvent(
    val title: String,
    val description: String,
    val start: Instant,
    /** An estimate, see [estimatedSeriesDuration]: the API gives no duration. */
    val end: Instant,
)

/** Hours assumed for a series whose format is unknown: those of a best of three, the most common format. */
const val UNKNOWN_FORMAT_HOURS = 3L

/**
 * One hour per possible game: a best of five may last five hours, a best of one lasts one. A format without a
 * known type or count is taken as [UNKNOWN_FORMAT_HOURS].
 */
fun estimatedSeriesDuration(format: SeriesFormat?): Duration {
    val games = format?.count?.takeIf { it > 0 && format.type != SeriesType.UNKNOWN }
    return Duration.ofHours(games?.toLong() ?: UNKNOWN_FORMAT_HOURS)
}

/**
 * The calendar event of [detail] starting at [startTime], or null when there is nothing to add: the series has
 * started or ended, its start time is not known, or it does not have two teams yet.
 */
fun calendarEventOf(detail: MatchDetail, startTime: Instant?): CalendarEvent? {
    if (startTime == null || detail.state != MatchState.UNSTARTED || detail.teams.size != 2) return null
    val league = detail.leagueName.trim().takeIf { it.isNotEmpty() }
    val format = detail.format?.label
    return CalendarEvent(
        title = listOfNotNull("${detail.teams[0].name} vs ${detail.teams[1].name}", league, format).joinToString(SEPARATOR),
        description = listOfNotNull(league, detail.blockName?.takeIf { it.isNotBlank() }, format).joinToString(SEPARATOR) +
            "\nThe end time is an estimate: one hour per possible game.",
        start = startTime,
        end = startTime.plus(estimatedSeriesDuration(detail.format)),
    )
}

private const val SEPARATOR = " · "
