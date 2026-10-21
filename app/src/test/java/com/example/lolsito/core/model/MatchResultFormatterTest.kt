package com.example.lolsito.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultFormatterTest {

    private fun bo(count: Int) = SeriesFormat(SeriesType.BEST_OF, count)

    private fun playAll(count: Int) = SeriesFormat(SeriesType.PLAY_ALL, count)

    private fun team(code: String, outcome: MatchOutcome? = null, wins: Int? = null) =
        Team(name = code, code = code, imageUrl = null, outcome = outcome, gameWins = wins)

    @Test
    fun `completed with outcome shows score and winner from outcome`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", MatchOutcome.LOSS, 1), team("GEN", MatchOutcome.WIN, 3)),
            format = bo(5),
        )
        assertEquals("1 - 3", result.score)
        assertEquals(1, result.winnerIndex)
        assertEquals("Bo5", result.formatLabel)
        assertEquals(TeamEmphasis.LOSER, result.emphasisFor(0))
        assertEquals(TeamEmphasis.WINNER, result.emphasisFor(1))
    }

    @Test
    fun `outcome wins over game wins when they disagree`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", MatchOutcome.WIN, 0), team("GEN", MatchOutcome.LOSS, 1)),
            format = bo(1),
        )
        assertEquals(0, result.winnerIndex)
    }

    @Test
    fun `completed without outcome derives winner from game wins`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("MVK", wins = 3), team("FUR", wins = 0)),
            format = bo(5),
        )
        assertEquals("3 - 0", result.score)
        assertEquals(0, result.winnerIndex)
    }

    @Test
    fun `unstarted shows no score and no winner but keeps best of`() {
        val result = formatMatchResult(
            MatchState.UNSTARTED,
            listOf(team("T1"), team("GEN")),
            format = bo(3),
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertEquals("Bo3", result.formatLabel)
        assertEquals(TeamEmphasis.NEUTRAL, result.emphasisFor(0))
        assertEquals(TeamEmphasis.NEUTRAL, result.emphasisFor(1))
    }

    @Test
    fun `in progress shows no score and no winner`() {
        val result = formatMatchResult(
            MatchState.IN_PROGRESS,
            listOf(team("T1", wins = 1), team("GEN", wins = 0)),
            format = bo(5),
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
    }

    @Test
    fun `completed tie shows score without winner`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 1), team("GEN", wins = 1)),
            format = bo(2),
        )
        assertEquals("1 - 1", result.score)
        assertNull(result.winnerIndex)
    }

    @Test
    fun `completed with missing game wins shows no score`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 2), team("GEN")),
            format = null,
        )
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertNull(result.formatLabel)
    }

    @Test
    fun `fewer than two teams shows nothing`() {
        val result = formatMatchResult(MatchState.COMPLETED, listOf(team("T1", MatchOutcome.WIN, 1)), format = bo(0))
        assertNull(result.score)
        assertNull(result.winnerIndex)
        assertNull(result.formatLabel)
    }

    @Test
    fun `play all series is labelled by game count`() {
        val result = formatMatchResult(MatchState.UNSTARTED, listOf(team("T1"), team("GEN")), format = playAll(2))
        assertEquals("2 games", result.formatLabel)
    }

    @Test
    fun `completed play all series with every game played shows score and winner`() {
        val result = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 2), team("GEN", wins = 1)),
            format = playAll(3),
        )
        assertEquals("2 - 1", result.score)
        assertEquals(0, result.winnerIndex)
        assertEquals("3 games", result.formatLabel)
    }

    @Test
    fun `play all series with games left is not shown as decided`() {
        val running = formatMatchResult(
            MatchState.COMPLETED,
            listOf(team("T1", wins = 2), team("GEN", wins = 0)),
            format = playAll(3),
        )
        assertNull(running.score)
        assertNull(running.winnerIndex)

        val inProgress = formatMatchResult(
            MatchState.IN_PROGRESS,
            listOf(team("T1", wins = 2), team("GEN", wins = 0)),
            format = playAll(3),
        )
        assertNull(inProgress.score)
        assertNull(inProgress.winnerIndex)
    }

    @Test
    fun `unknown series type has no label`() {
        val result = formatMatchResult(
            MatchState.UNSTARTED,
            listOf(team("T1"), team("GEN")),
            format = SeriesFormat(SeriesType.UNKNOWN, 2),
        )
        assertNull(result.formatLabel)
    }
}
