package com.example.lolsito.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultFormatterTest {

    private fun team(code: String, outcome: MatchOutcome? = null, wins: Int? = null) =
        Team(name = code, code = code, imageUrl = null, outcome = outcome, gameWins = wins)

    @Test
    fun `completed with outcome shows score and winner from outcome`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", MatchOutcome.LOSS, 1), team("GEN", MatchOutcome.WIN, 3)),
            bestOf = 5,
        )
        assertEquals("1 - 3", result.score)
        assertEquals(1, result.winnerIndex)
        assertEquals("Bo5", result.bestOf)
        assertEquals(TeamEmphasis.LOSER, result.emphasisFor(0))
        assertEquals(TeamEmphasis.WINNER, result.emphasisFor(1))
    }

    @Test
    fun `outcome wins over game wins when they disagree`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", MatchOutcome.WIN, 0), team("GEN", MatchOutcome.LOSS, 1)),
            bestOf = 1,
        )
        assertEquals(0, result.winnerIndex)
    }

    @Test
    fun `completed without outcome derives winner from game wins`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("MVK", wins = 3), team("FUR", wins = 0)),
            bestOf = 5,
        )
        assertEquals("3 - 0", result.score)
        assertEquals(0, result.winnerIndex)
    }

    @Test
    fun `unstarted shows no score and no winner but keeps best of`() {
        val result = formatMatchResult(
            MatchState.UNSTARTED,
            listOf(team("T1"), team("GEN")),
            bestOf = 3,
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertEquals("Bo3", result.bestOf)
        assertEquals(TeamEmphasis.NEUTRAL, result.emphasisFor(0))
        assertEquals(TeamEmphasis.NEUTRAL, result.emphasisFor(1))
    }

    @Test
    fun `in progress shows no score and no winner`() {
        val result = formatMatchResult(
            MatchState.IN_PROGRESS,
            listOf(team("T1", wins = 1), team("GEN", wins = 0)),
            bestOf = 5,
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
    }

    @Test
    fun `completed tie shows score without winner`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 1), team("GEN", wins = 1)),
            bestOf = 2,
        )
        assertEquals("1 - 1", result.score)
        assertNull(result.winnerIndex)
    }

    @Test
    fun `completed with missing game wins shows no score`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 2), team("GEN")),
            bestOf = null,
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertNull(result.bestOf)
    }

    @Test
    fun `fewer than two teams shows nothing`() {
        val result = formatMatchResult(MatchState.COMPLETED, listOf(team("T1", MatchOutcome.WIN, 1)), bestOf = 0)
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertNull(result.bestOf)
    }
}
