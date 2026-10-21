package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleGroupingTest {

    private fun event(id: String, time: String) = ScheduleEvent(
        startTime = Instant.parse(time),
        state = MatchState.UNSTARTED,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(id = id, teams = emptyList(), format = null),
    )

    private fun matchless(time: String, state: MatchState = MatchState.UNSTARTED, block: String? = "Finals") =
        ScheduleEvent(
            startTime = Instant.parse(time),
            state = state,
            blockName = block,
            leagueName = "Worlds",
            leagueSlug = "worlds",
            match = null,
        )

    @Test
    fun `groups by local day respecting time zone boundary`() {
        val events = listOf(
            event("a", "2026-10-20T02:00:00Z"),
            event("b", "2026-10-20T06:00:00Z"),
            event("c", "2026-10-21T03:59:00Z"),
        )

        val caracas = groupEventsByDay(events, ZoneId.of("America/Caracas")) // UTC-4
        assertEquals(
            listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20)),
            caracas.map { it.date },
        )
        assertEquals(listOf("a"), caracas[0].events.map { it.matchId })
        assertEquals(listOf("b", "c"), caracas[1].events.map { it.matchId })

        val utc = groupEventsByDay(events, ZoneId.of("UTC"))
        assertEquals(listOf(listOf("a", "b"), listOf("c")), utc.map { day -> day.events.map { it.matchId } })
    }

    @Test
    fun `merge removes duplicates and keeps chronological order`() {
        val existing = listOf(event("b", "2026-10-20T06:00:00Z"), event("c", "2026-10-21T06:00:00Z"))
        val incoming = listOf(event("a", "2026-10-19T06:00:00Z"), event("b", "2026-10-20T06:00:00Z"))

        val merged = mergeEvents(existing, incoming)

        assertEquals(listOf("a", "b", "c"), merged.map { it.matchId })
    }

    @Test
    fun `merge replaces an existing match with the fresher incoming copy`() {
        val stale = event("x", "2026-10-20T06:00:00Z")
        val fresh = stale.copy(
            state = MatchState.COMPLETED,
            match = Match(
                id = "x",
                teams = listOf(
                    Team("T1", "T1", null, MatchOutcome.WIN, 2),
                    Team("Gen.G", "GEN", null, MatchOutcome.LOSS, 1),
                ),
                format = SeriesFormat(SeriesType.BEST_OF, 3),
            ),
        )
        val other = event("y", "2026-10-21T06:00:00Z")

        val merged = mergeEvents(listOf(stale, other), listOf(fresh))

        assertEquals(listOf(fresh, other), merged)
    }

    @Test
    fun `distinct match-less events at the same time, league and block both survive merge`() {
        val first = matchless("2026-10-20T06:00:00Z", state = MatchState.COMPLETED)
        val second = matchless("2026-10-20T06:00:00Z", state = MatchState.UNSTARTED)

        val merged = mergeEvents(listOf(first), listOf(second))

        assertEquals(2, merged.size)
        assertEquals(setOf(first, second), merged.toSet())
    }

    @Test
    fun `identical match-less events are deduplicated`() {
        val event = matchless("2026-10-20T06:00:00Z")

        val merged = mergeEvents(listOf(event), listOf(event.copy()))

        assertEquals(listOf(event), merged)
    }

    @Test
    fun `merge keeps chronological order across matched and match-less events`() {
        val late = event("late", "2026-10-21T06:00:00Z")
        val early = matchless("2026-10-19T06:00:00Z")
        val middle = event("middle", "2026-10-20T06:00:00Z")

        val merged = mergeEvents(listOf(late), listOf(early, middle))

        assertEquals(listOf(early, middle, late), merged)
    }

    @Test
    fun `day item keys are unique even for colliding match-less events`() {
        val events = listOf(
            event("a", "2026-10-20T06:00:00Z"),
            matchless("2026-10-20T06:00:00Z", state = MatchState.COMPLETED),
            matchless("2026-10-20T06:00:00Z", state = MatchState.UNSTARTED),
            matchless("2026-10-20T07:00:00Z", block = null),
        )

        val days = groupEventsByDay(events, ZoneId.of("UTC"))
        val keys = days.flatMap { it.keys }

        assertEquals(events.size, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(days.single().events.size, days.single().keys.size)
    }

    @Test
    fun `match events are keyed by match id`() {
        val days = groupEventsByDay(listOf(event("a", "2026-10-20T06:00:00Z")), ZoneId.of("UTC"))

        assertEquals(listOf("match:a"), days.single().keys)
    }
}
