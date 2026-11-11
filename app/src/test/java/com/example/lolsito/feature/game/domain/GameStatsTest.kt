package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GameStatsTest {

    private fun player(id: Int) = PlayerStats(id, "P$id", "Olaf", 0, 0, 0, 0, 1)

    private fun team(vararg ids: Int) = TeamStats(0, 0, 0, 0, 0, ids.map(::player))

    @Test
    fun `player keys follow participant ids`() {
        assertEquals(listOf("player:1#0", "player:2#0"), team(1, 2).playerKeys)
    }

    @Test
    fun `player keys stay unique when a participant id is repeated`() {
        assertEquals(listOf("player:1#0", "player:1#1"), team(1, 1).playerKeys)
    }
}
