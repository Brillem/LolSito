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
    fun `game route keeps the game id path and takes optional team codes`() {
        assertEquals("game/{gameId}?blueCode={blueCode}&redCode={redCode}", Routes.GAME)
        assertEquals("gameId", Routes.GAME_ID_ARG)
        assertEquals("blueCode", Routes.BLUE_CODE_ARG)
        assertEquals("redCode", Routes.RED_CODE_ARG)
    }

    private val fakeEncode: (String) -> String = { "<$it>" }

    @Test
    fun `game route encodes the id and both codes`() {
        assertEquals("game/<g1>?blueCode=<MVK>&redCode=<FUR>", gameRoute("g1", "MVK", "FUR", fakeEncode))
    }

    @Test
    fun `game route leaves out unknown codes`() {
        assertEquals("game/<g1>", gameRoute("g1", null, null, fakeEncode))
        assertEquals("game/<g1>?redCode=<FUR>", gameRoute("g1", null, "FUR", fakeEncode))
        assertEquals("game/<g1>?blueCode=<MVK>", gameRoute("g1", "MVK", null, fakeEncode))
    }
}
