package com.example.lolsito.core.network

import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.dto.EventDetailTeamDto
import com.example.lolsito.core.network.dto.ResultDto
import com.example.lolsito.core.network.dto.StandingsTeamDto
import com.example.lolsito.core.network.dto.TeamDto
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamMappersTest {
    private val result = ResultDto(outcome = "win", gameWins = 2)
    private val expected = Team(
        name = "T1",
        code = "T1",
        imageUrl = "https://a.com/t1.png",
        outcome = MatchOutcome.WIN,
        gameWins = 2,
        id = "t1-id",
    )

    @Test
    fun `schedule team maps every field except the id it does not send`() {
        val team = TeamDto(name = "T1", code = "T1", image = "http://a.com/t1.png", result = result).toTeam()
        assertEquals(expected.copy(id = null), team)
    }

    @Test
    fun `event detail and standings teams also map the id`() {
        val detail = EventDetailTeamDto(id = "t1-id", name = "T1", code = "T1", image = "http://a.com/t1.png", result = result)
        val standings = StandingsTeamDto(id = "t1-id", name = "T1", code = "T1", image = "http://a.com/t1.png", result = result)
        assertEquals(expected, detail.toTeam())
        assertEquals(expected, standings.toTeam())
    }

    @Test
    fun `missing fields fall back to TBD and null`() {
        val expectedTbd = Team(name = "TBD", code = "TBD", imageUrl = null, outcome = null, gameWins = null, id = null)
        assertEquals(expectedTbd, TeamDto().toTeam())
        assertEquals(expectedTbd, EventDetailTeamDto(id = " ", code = " ").toTeam())
        assertEquals(expectedTbd, StandingsTeamDto(id = "", code = "").toTeam())
    }
}
