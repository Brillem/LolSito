package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.League
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Rows of the "Upcoming tournaments" section. */
const val MAX_UPCOMING_TOURNAMENTS = 5

/** Furthest distance, in days, that is spelled out next to the start date ("in 12 days"). */
internal const val NEAR_START_DAYS = 30L

/** A tournament that has not started yet, ready to show: [name] is the readable label, [startDate] is after today. */
data class UpcomingTournament(
    val leagueId: String,
    val tournamentId: String,
    val name: String,
    val startDate: LocalDate,
    val leagueImageUrl: String?,
)

/**
 * The tournaments that start after [today], soonest start first, at most [count].
 *
 * [tournamentsByLeague] holds each league's tournaments in league order; a tournament without a start date, or
 * starting today or before, is not upcoming. Ties on the start date go to the earlier league of the input, then
 * to the name. A tournament listed twice (the same league given twice) shows once. The league logo comes from
 * [leagues], null when the league is not there.
 */
fun upcomingTournaments(
    tournamentsByLeague: List<Pair<String, List<Tournament>>>,
    leagues: List<League>,
    today: LocalDate,
    count: Int = MAX_UPCOMING_TOURNAMENTS,
): List<UpcomingTournament> {
    val imageByLeague = leagues.associate { it.id to it.imageUrl }
    return tournamentsByLeague
        .flatMapIndexed { leagueIndex, (leagueId, tournaments) ->
            tournaments.mapNotNull { tournament ->
                val start = tournament.startDate?.takeIf { it.isAfter(today) } ?: return@mapNotNull null
                leagueIndex to UpcomingTournament(leagueId, tournament.id, tournament.label, start, imageByLeague[leagueId])
            }
        }
        .distinctBy { (_, upcoming) -> upcoming.tournamentId }
        .sortedWith(
            compareBy<Pair<Int, UpcomingTournament>> { it.second.startDate }
                .thenBy { it.first }
                .thenBy { it.second.name },
        )
        .take(count)
        .map { it.second }
}

/**
 * The upcoming tournaments of [leagueIds] (in that order, see [upcomingTournaments]). Every league is asked
 * once, concurrently, and a league whose request fails is skipped, so the list can come back empty.
 */
suspend fun StandingsRepository.loadUpcomingTournaments(
    leagueIds: List<String>,
    leagues: List<League>,
    today: LocalDate,
): List<UpcomingTournament> = coroutineScope {
    val byLeague = leagueIds.distinct().map { leagueId ->
        async {
            try {
                leagueId to getTournaments(leagueId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                leagueId to emptyList()
            }
        }
    }.awaitAll()
    upcomingTournaments(byLeague, leagues, today)
}

/**
 * "starts Mar 10": the month abbreviated and the day in [locale]; the year is added when it is not the year
 * of [today] ("starts Jan 14, 2028").
 */
fun startsLabel(startDate: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = if (startDate.year == today.year) "MMM d" else "MMM d, yyyy"
    return "starts ${startDate.format(DateTimeFormatter.ofPattern(pattern, locale))}"
}

/**
 * How far the start is: "tomorrow" or "in 12 days", up to [NEAR_START_DAYS] days; null further away, and for a
 * start that is today or already past.
 */
fun startsInLabel(startDate: LocalDate, today: LocalDate): String? {
    val days = ChronoUnit.DAYS.between(today, startDate)
    return when {
        days < 1 || days > NEAR_START_DAYS -> null
        days == 1L -> "tomorrow"
        else -> "in $days days"
    }
}
