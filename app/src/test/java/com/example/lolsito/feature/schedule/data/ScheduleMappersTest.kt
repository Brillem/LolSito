package com.example.lolsito.feature.schedule.data

import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.EventDto
import com.example.lolsito.core.network.dto.ScheduleResponse
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScheduleMappersTest {

    private val schedule = LolEsportsJson.decodeFromString<ScheduleResponse>(
        Fixtures.read("getSchedule_worlds.json")
    ).data.schedule

    @Test
    fun `schedule maps all events and page tokens`() {
        val page = schedule.toSchedulePage()
        assertEquals(80, page.events.size)
        assertEquals("b2xkZXI6OjExMzQ3NTc5ODAwNjY2NDU3NA==", page.olderToken)
        assertEquals("bmV3ZXI6OjExNTY2MDU0MDcyNjA5NTEwOQ==", page.newerToken)
    }

    @Test
    fun `known event fields are mapped`() {
        val event = schedule.toSchedulePage().events.first()
        assertEquals(Instant.parse("2025-10-15T07:00:00Z"), event.startTime)
        assertEquals(MatchState.COMPLETED, event.state)
        assertEquals("Swiss", event.blockName)
        assertEquals("Worlds", event.leagueName)
        val match = event.match!!
        assertEquals("113475798006664574", match.id)
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 1), match.format)
        assertEquals(listOf("MKOI", "KT"), match.teams.map { it.code })
        assertEquals(MatchOutcome.WIN, match.teams[1].outcome)
        assertEquals(1, match.teams[1].gameWins)
        assertEquals(
            "https://static.lolesports.com/teams/1734012609283_MKOI_FullColor_Blue.png",
            match.teams[0].imageUrl
        )
    }

    @Test
    fun `unstarted TBD event tolerates null result and record`() {
        val event = schedule.toSchedulePage().events.last()
        assertEquals(MatchState.UNSTARTED, event.state)
        val teams = event.match!!.teams
        assertTrue(teams.all { it.code == "TBD" && it.outcome == null && it.gameWins == null })
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 5), event.match!!.format)
    }

    @Test
    fun `state strings map to enum with unknown fallback`() {
        assertEquals(MatchState.IN_PROGRESS, "inProgress".toMatchState())
        assertEquals(MatchState.UNKNOWN, "weird".toMatchState())
        assertEquals(MatchState.UNKNOWN, null.toMatchState())
    }

    @Test
    fun `event without match or teams still maps`() {
        val dto = LolEsportsJson.decodeFromString<EventDto>(
            """{"startTime":"2026-01-01T10:00:00Z","state":"unstarted","blockName":null,"league":{"name":"LCK"}}"""
        )
        val event = dto.toDomain()!!
        assertNull(event.match)
        assertEquals("LCK", event.leagueName)
        assertNull(event.matchId)
    }

    @Test
    fun `play all strategy maps to a play all series format`() {
        val dto = LolEsportsJson.decodeFromString<EventDto>(
            """{"startTime":"2026-01-01T10:00:00Z","state":"unstarted",""" +
                """"match":{"id":"m1","teams":[],"strategy":{"type":"playAll","count":2}}}"""
        )
        assertEquals(SeriesFormat(SeriesType.PLAY_ALL, 2), dto.toDomain()!!.match!!.format)
    }

    @Test
    fun `event with unparseable start time is dropped`() {
        val dto = LolEsportsJson.decodeFromString<EventDto>("""{"state":"unstarted"}""")
        assertNull(dto.toDomain())
    }
}
