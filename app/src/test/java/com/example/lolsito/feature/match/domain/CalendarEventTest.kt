package com.example.lolsito.feature.match.domain

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CalendarEventTest {

    private val start = Instant.parse("2026-11-20T17:00:00Z")

    private fun detail(
        state: MatchState = MatchState.UNSTARTED,
        format: SeriesFormat? = SeriesFormat(SeriesType.BEST_OF, 3),
        teams: List<Team> = listOf(Team("T1", "T1", null, null, null), Team("Gen.G", "GEN", null, null, null)),
        leagueName: String = "Worlds",
        blockName: String? = "Finals",
    ) = MatchDetail(
        matchId = "m",
        state = state,
        leagueName = leagueName,
        leagueSlug = "worlds",
        leagueImageUrl = null,
        blockName = blockName,
        teams = teams,
        format = format,
        games = emptyList(),
    )

    @Test
    fun `the event names both teams, the league and the format, and starts at the match time`() {
        val event = calendarEventOf(detail(), start)

        assertEquals(
            CalendarEvent(
                title = "T1 vs Gen.G · Worlds · Bo3",
                description = "Worlds · Finals · Bo3\nThe end time is an estimate: one hour per possible game.",
                start = start,
                end = start.plus(Duration.ofHours(3)),
            ),
            event,
        )
    }

    @Test
    fun `the estimated duration is one hour per possible game`() {
        fun hours(format: SeriesFormat?) = estimatedSeriesDuration(format).toHours()

        assertEquals(1, hours(SeriesFormat(SeriesType.BEST_OF, 1)))
        assertEquals(3, hours(SeriesFormat(SeriesType.BEST_OF, 3)))
        assertEquals(5, hours(SeriesFormat(SeriesType.BEST_OF, 5)))
        assertEquals(2, hours(SeriesFormat(SeriesType.PLAY_ALL, 2)))
    }

    @Test
    fun `an unknown format is estimated as three hours and left out of the texts`() {
        assertEquals(3, estimatedSeriesDuration(null).toHours())
        assertEquals(3, estimatedSeriesDuration(SeriesFormat(SeriesType.UNKNOWN, 5)).toHours())
        assertEquals(3, estimatedSeriesDuration(SeriesFormat(SeriesType.BEST_OF, 0)).toHours())

        val event = calendarEventOf(detail(format = null, blockName = null), start)
        assertEquals("T1 vs Gen.G · Worlds", event?.title)
        assertEquals("Worlds\nThe end time is an estimate: one hour per possible game.", event?.description)
        assertEquals(start.plus(Duration.ofHours(3)), event?.end)
    }

    @Test
    fun `a blank league is left out of the title`() {
        assertEquals("T1 vs Gen.G · Bo3", calendarEventOf(detail(leagueName = " ", blockName = null), start)?.title)
    }

    @Test
    fun `there is no event without a start time`() {
        assertNull(calendarEventOf(detail(), startTime = null))
    }

    @Test
    fun `there is no event once the series has started or ended`() {
        assertNull(calendarEventOf(detail(MatchState.IN_PROGRESS), start))
        assertNull(calendarEventOf(detail(MatchState.COMPLETED), start))
    }

    @Test
    fun `there is no event while a team is still to be decided`() {
        assertNull(calendarEventOf(detail(teams = listOf(Team("T1", "T1", null, null, null))), start))
    }
}
