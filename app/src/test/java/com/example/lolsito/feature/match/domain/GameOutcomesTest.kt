package com.example.lolsito.feature.match.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GameOutcomesTest {

    // Winners by team index (0 or 1), in game order; null is a game whose stats do not tell.

    @Test
    fun `known winners are kept when they add up to the series score`() {
        assertEquals(listOf(0, 1, 0), completeGameWinners(listOf(0, 1, 0), gameWins = listOf(2, 1)))
    }

    @Test
    fun `in a sweep every game goes to the team with all the wins`() {
        assertEquals(listOf(1, 1, 1), completeGameWinners(listOf(null, 1, null), gameWins = listOf(0, 3)))
    }

    @Test
    fun `the last unknown game goes to the team still missing a win`() {
        assertEquals(listOf(0, 1, 0), completeGameWinners(listOf(0, 1, null), gameWins = listOf(2, 1)))
        assertEquals(listOf(1, 0, 0), completeGameWinners(listOf(null, 0, 0), gameWins = listOf(2, 1)))
    }

    @Test
    fun `unknown games that could go either way stay unknown`() {
        assertEquals(listOf(null, null, 0), completeGameWinners(listOf(null, null, 0), gameWins = listOf(2, 1)))
    }

    @Test
    fun `winners that contradict the series score are all dropped`() {
        assertEquals(listOf(null, null, null), completeGameWinners(listOf(0, 0, 0), gameWins = listOf(2, 1)))
        assertEquals(listOf(null, null), completeGameWinners(listOf(0, 1), gameWins = listOf(2, 1)))
    }

    @Test
    fun `without a series score only what the stats tell is kept`() {
        assertEquals(listOf(0, null), completeGameWinners(listOf(0, null), gameWins = listOf(null, null)))
        assertEquals(listOf(0, null), completeGameWinners(listOf(0, null), gameWins = listOf(1)))
    }

    @Test
    fun `each game carries the series score after it`() {
        assertEquals(
            listOf(GameOutcome(0, "1 - 0"), GameOutcome(1, "1 - 1"), GameOutcome(0, "2 - 1")),
            gameOutcomes(listOf(0, 1, 0)),
        )
    }

    @Test
    fun `after an unknown game the winners are still shown, without a running score`() {
        assertEquals(
            listOf(GameOutcome(0, "1 - 0"), null, GameOutcome(0, null)),
            gameOutcomes(listOf(0, null, 0)),
        )
    }
}
