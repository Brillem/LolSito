package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameWinnerTest {

    private fun team(towers: Int, inhibitors: Int) =
        TeamStats(totalGold = 60_000, kills = 10, towers = towers, dragons = 2, barons = 1, players = emptyList(), inhibitors = inhibitors)

    private fun stats(blue: TeamStats, red: TeamStats, finished: Boolean = true) =
        GameStats(isFinished = finished, blueTeam = blue, redTeam = red, blueTeamId = "blue-id", redTeamId = "red-id")

    @Test
    fun `the only team that took an inhibitor and five towers won`() {
        assertEquals(MapSide.BLUE, stats(team(towers = 9, inhibitors = 2), team(towers = 3, inhibitors = 0)).winnerSide())
        assertEquals(MapSide.RED, stats(team(towers = 2, inhibitors = 0), team(towers = 5, inhibitors = 1)).winnerSide())
    }

    @Test
    fun `towers alone or an inhibitor alone do not reach the nexus`() {
        assertNull(stats(team(towers = 9, inhibitors = 0), team(towers = 3, inhibitors = 0)).winnerSide())
        assertNull(stats(team(towers = 4, inhibitors = 1), team(towers = 3, inhibitors = 0)).winnerSide())
    }

    @Test
    fun `when both teams could have taken the nexus the winner is unknown`() {
        assertNull(stats(team(towers = 10, inhibitors = 2), team(towers = 7, inhibitors = 1)).winnerSide())
    }

    @Test
    fun `a game that is not finished has no winner`() {
        assertNull(stats(team(towers = 9, inhibitors = 2), team(towers = 3, inhibitors = 0), finished = false).winnerSide())
    }

    @Test
    fun `the winner's team id is the one the feed puts on that side`() {
        assertEquals("blue-id", stats(team(9, 2), team(3, 0)).winnerTeamId())
        assertEquals("red-id", stats(team(3, 0), team(9, 2)).winnerTeamId())
        assertNull(stats(team(3, 0), team(3, 0)).winnerTeamId())
    }
}
