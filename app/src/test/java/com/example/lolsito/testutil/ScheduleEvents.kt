package com.example.lolsito.testutil

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import java.time.Instant

/**
 * A schedule event with a match of [first] against [second] on January [day], 2026 (UTC noon), in the league
 * "League". [firstWon]: true when the first team won, false when the second did, null for no known winner.
 * [firstWins] and [secondWins] are the game wins. A team's code is the first three letters of its name, in upper case.
 */
fun series(
    id: String,
    day: Int,
    first: String,
    second: String,
    firstWon: Boolean? = null,
    state: MatchState = MatchState.COMPLETED,
    firstWins: Int? = null,
    secondWins: Int? = null,
) = ScheduleEvent(
    startTime = Instant.parse("2026-01-%02dT12:00:00Z".format(day)),
    state = state,
    blockName = null,
    leagueName = "League",
    leagueSlug = "league",
    match = Match(
        id = id,
        teams = listOf(
            scheduleTeam(first, firstWon?.let { if (it) MatchOutcome.WIN else MatchOutcome.LOSS }, firstWins),
            scheduleTeam(second, firstWon?.let { if (it) MatchOutcome.LOSS else MatchOutcome.WIN }, secondWins),
        ),
        format = SeriesFormat(SeriesType.BEST_OF, 3),
    ),
)

private fun scheduleTeam(name: String, outcome: MatchOutcome?, wins: Int?) =
    Team(name, name.trim().take(3).uppercase(), null, outcome, wins)
