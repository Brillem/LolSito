package com.example.lolsito.navigation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchNavigationTest {

    @Test
    fun `navigation starts only from a resumed entry`() {
        assertTrue(canNavigateFrom(Lifecycle.State.RESUMED))
    }

    @Test
    fun `an entry that is already leaving does not navigate again`() {
        assertFalse(canNavigateFrom(Lifecycle.State.STARTED))
        assertFalse(canNavigateFrom(Lifecycle.State.CREATED))
        assertFalse(canNavigateFrom(Lifecycle.State.DESTROYED))
    }

    @Test
    fun `game route keeps the game id path and takes optional team arguments`() {
        assertEquals(
            "game/{gameId}?team1Id={team1Id}&team1Code={team1Code}&team2Id={team2Id}&team2Code={team2Code}",
            Routes.GAME,
        )
        assertEquals("gameId", Routes.GAME_ID_ARG)
        assertEquals(listOf("team1Id", "team1Code", "team2Id", "team2Code"), Routes.TEAM_ARGS)
    }

    private val fakeEncode: (String) -> String = { "<$it>" }

    @Test
    fun `game route encodes the id and both teams`() {
        assertEquals(
            "game/<g1>?team1Id=<107>&team1Code=<T1>&team2Id=<108>&team2Code=<100T>",
            gameRoute("g1", linkedMapOf("107" to "T1", "108" to "100T"), fakeEncode),
        )
    }

    @Test
    fun `game route leaves out teams that are not known`() {
        assertEquals("game/<g1>", gameRoute("g1", emptyMap(), fakeEncode))
        assertEquals("game/<g1>?team1Id=<107>&team1Code=<T1>", gameRoute("g1", mapOf("107" to "T1"), fakeEncode))
    }

    @Test
    fun `game route keeps at most two teams`() {
        val route = gameRoute("g1", linkedMapOf("1" to "A", "2" to "B", "3" to "C"), fakeEncode)
        assertEquals("game/<g1>?team1Id=<1>&team1Code=<A>&team2Id=<2>&team2Code=<B>", route)
    }

    @Test
    fun `team codes are read back from the route arguments`() {
        val args = mapOf("team1Id" to "107", "team1Code" to "T1", "team2Id" to "108", "team2Code" to "100T")
        assertEquals(mapOf("107" to "T1", "108" to "100T"), gameTeamCodes(args::get))
    }

    @Test
    fun `an incomplete team argument pair is ignored`() {
        val args = mapOf("team1Id" to "107", "team2Id" to "108", "team2Code" to "100T")
        assertEquals(mapOf("108" to "100T"), gameTeamCodes(args::get))
        assertEquals(emptyMap<String, String>(), gameTeamCodes { null })
    }
}
