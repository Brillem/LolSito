package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleSectionsTest {

    private val utc = ZoneId.of("UTC")

    private fun event(
        id: String,
        time: String,
        state: MatchState = MatchState.UNSTARTED,
        leagueId: String? = WORLDS,
    ) = ScheduleEvent(
        startTime = Instant.parse(time),
        state = state,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(id = id, teams = emptyList(), format = null),
        leagueId = leagueId,
    )

    private fun matchless(time: String, state: MatchState) = ScheduleEvent(
        startTime = Instant.parse(time),
        state = state,
        blockName = "Finals",
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = null,
    )

    private fun ids(days: List<ScheduleDay>) = days.map { day -> day.events.map { it.matchId } }

    private fun sections(
        events: List<ScheduleEvent>,
        feed: List<ScheduleEvent>? = emptyList(),
        leagues: Set<String>? = setOf(WORLDS),
    ) = buildScheduleSections(events, feed, leagues, utc)

    @Test
    fun `upcoming matches run soonest first, grouped by ascending day`() {
        val result = sections(
            listOf(
                event("c", "2026-10-22T10:00:00Z"),
                event("a", "2026-10-21T08:00:00Z"),
                event("b", "2026-10-21T12:00:00Z"),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22)), result.upcoming.map { it.date })
        assertEquals(listOf(listOf("a", "b"), listOf("c")), ids(result.upcoming))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `results run most recent first, grouped by descending day`() {
        val done = MatchState.COMPLETED
        val result = sections(
            listOf(
                event("a", "2026-10-19T08:00:00Z", done),
                event("c", "2026-10-20T14:00:00Z", done),
                event("b", "2026-10-20T09:00:00Z", done),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 19)), result.results.map { it.date })
        assertEquals(listOf(listOf("c", "b"), listOf("a")), ids(result.results))
        assertTrue(result.upcoming.isEmpty())
    }

    @Test
    fun `completed and unstarted events land in their own section`() {
        val result = sections(
            listOf(
                event("past", "2026-10-19T08:00:00Z", MatchState.COMPLETED),
                event("next", "2026-10-21T08:00:00Z"),
            ),
        )

        assertEquals(listOf(listOf("next")), ids(result.upcoming))
        assertEquals(listOf(listOf("past")), ids(result.results))
    }

    @Test
    fun `a match the live feed reports is only in the live section`() {
        val started = event("started", "2026-10-20T08:00:00Z") // the schedule has not caught up yet
        val liveCopy = event("started", "2026-10-20T08:00:00Z", MatchState.IN_PROGRESS)
        val result = sections(listOf(started, event("later", "2026-10-20T12:00:00Z")), feed = listOf(liveCopy))

        assertEquals(listOf("started"), result.live.map { it.matchId })
        assertEquals(listOf(listOf("later")), ids(result.upcoming))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `live events are the feed filtered by the leagues and sorted by start`() {
        val feed = listOf(
            event("w2", "2026-10-20T10:00:00Z", MatchState.IN_PROGRESS, WORLDS),
            event("k1", "2026-10-20T09:00:00Z", MatchState.IN_PROGRESS, LCK),
            event("w1", "2026-10-20T08:00:00Z", MatchState.IN_PROGRESS, WORLDS),
        )

        assertEquals(listOf("w1", "w2"), sections(emptyList(), feed).live.map { it.matchId })
        assertEquals(listOf("w1", "k1", "w2"), sections(emptyList(), feed, leagues = setOf(WORLDS, LCK)).live.map { it.matchId })
        assertEquals(listOf("w1", "k1", "w2"), sections(emptyList(), feed, leagues = null).live.map { it.matchId })
        assertTrue(sections(emptyList(), feed, leagues = emptySet()).live.isEmpty())
    }

    @Test
    fun `without an answer from the live feed an in-progress match is live`() {
        val result = sections(
            listOf(
                event("on", "2026-10-20T08:00:00Z", MatchState.IN_PROGRESS),
                event("next", "2026-10-20T12:00:00Z"),
                event("done", "2026-10-19T08:00:00Z", MatchState.COMPLETED),
            ),
            feed = null,
        )

        assertEquals(listOf("on"), result.live.map { it.matchId })
        assertEquals(listOf(listOf("next")), ids(result.upcoming))
        assertEquals(listOf(listOf("done")), ids(result.results))
    }

    @Test
    fun `once the feed answered an in-progress match it does not list is not live`() {
        val result = sections(listOf(event("stale", "2026-10-20T08:00:00Z", MatchState.IN_PROGRESS)), feed = emptyList())

        assertTrue(result.live.isEmpty())
        assertEquals(listOf(listOf("stale")), ids(result.results))
    }

    @Test
    fun `a match-less in-progress event is live when the feed has not answered`() {
        val show = matchless("2026-10-20T08:00:00Z", MatchState.IN_PROGRESS)
        val result = sections(listOf(show), feed = null)

        assertEquals(listOf(show), result.live)
        assertTrue(result.upcoming.isEmpty())
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `no events and nothing live gives empty sections`() {
        val result = sections(emptyList())

        assertTrue(result.live.isEmpty())
        assertTrue(result.upcoming.isEmpty())
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `days respect the time zone in both sections`() {
        val caracas = ZoneId.of("America/Caracas") // UTC-4
        val result = buildScheduleSections(
            listOf(
                event("a", "2026-10-21T02:00:00Z"), // 20th, 22:00 local
                event("b", "2026-10-21T05:00:00Z"), // 21st, 01:00 local
                event("x", "2026-10-18T02:00:00Z", MatchState.COMPLETED), // 17th local
                event("y", "2026-10-18T05:00:00Z", MatchState.COMPLETED), // 18th local
            ),
            liveFeed = emptyList(),
            liveLeagueIds = setOf(WORLDS),
            zoneId = caracas,
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), result.upcoming.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 17)), result.results.map { it.date })
    }

    @Test
    fun `keys never collide across sections, even for match-less events and a day shared by both`() {
        val result = sections(
            listOf(
                event("a", "2026-10-20T06:00:00Z", MatchState.COMPLETED),
                event("b", "2026-10-20T18:00:00Z"),
                matchless("2026-10-20T07:00:00Z", MatchState.COMPLETED),
                matchless("2026-10-20T07:00:00Z", MatchState.UNSTARTED),
                matchless("2026-10-20T07:00:00Z", MatchState.UNSTARTED),
            ),
        )

        val keys = (result.upcoming + result.results).flatMap { it.keys + it.headerKey }

        assertTrue(keys.size >= 7)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `existing day grouping is unchanged`() {
        val days = groupEventsByDay(
            listOf(event("b", "2026-10-20T09:00:00Z"), event("a", "2026-10-19T09:00:00Z")),
            utc,
        )

        assertEquals(listOf(listOf("a"), listOf("b")), ids(days))
        assertEquals(listOf("match:a"), days.first().keys)
    }

    @Test
    fun `live score shows game wins only when both are known`() {
        fun team(wins: Int?) = Team("T", "T", null, null, wins)

        assertEquals("1 - 0", liveScoreLabel(listOf(team(1), team(0))))
        assertNull(liveScoreLabel(listOf(team(1), team(null))))
        assertNull(liveScoreLabel(listOf(team(1))))
        assertNull(liveScoreLabel(emptyList()))
    }

    private companion object {
        const val WORLDS = "w"
        const val LCK = "k"
    }
}
