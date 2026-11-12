package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FavoriteTeamsTest {

    private fun team(name: String) = Team(name, "XX", null, null, null)

    private fun event(vararg names: String, withMatch: Boolean = true) = ScheduleEvent(
        startTime = Instant.parse("2026-10-20T00:00:00Z"),
        state = MatchState.UNSTARTED,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = if (withMatch) Match(id = "m", teams = names.map(::team), format = null) else null,
    )

    @Test
    fun `a team is a favorite when its name matches, ignoring case and surrounding spaces`() {
        val names = listOf("T1", "Gen.G")
        assertTrue(team("T1").isFavoriteIn(names))
        assertTrue(team("t1").isFavoriteIn(names))
        assertTrue(team("  gen.g ").isFavoriteIn(names))
        assertTrue(team("T1").isFavoriteIn(listOf(" t1  ")))
    }

    @Test
    fun `a team that is not in the list is not a favorite, and a partial name does not match`() {
        assertFalse(team("Gen.G").isFavoriteIn(listOf("T1")))
        assertFalse(team("T1 Academy").isFavoriteIn(listOf("T1")))
        assertFalse(team("T1").isFavoriteIn(emptyList()))
    }

    @Test
    fun `an event involves a favorite when either of its teams is one`() {
        val names = listOf("T1")
        assertTrue(event("T1", "Gen.G").involvesFavorite(names))
        assertTrue(event("Gen.G", "t1").involvesFavorite(names))
        assertFalse(event("Gen.G", "KT Rolster").involvesFavorite(names))
        assertFalse(event("T1", "Gen.G").involvesFavorite(emptyList()))
    }

    @Test
    fun `an event without a match or teams involves no favorite`() {
        assertFalse(event(withMatch = false).involvesFavorite(listOf("T1")))
        assertFalse(event().involvesFavorite(listOf("T1")))
    }

    @Test
    fun `favorite names are the stored names trimmed, without blanks or repeats`() {
        assertEquals(
            listOf("T1", "Gen.G"),
            favoriteNamesOf(listOf(" T1 ", "Gen.G", "t1", "  ", "").mapIndexed { i, name -> FavoriteTeam("$i", "s$i", name, "X", null) }),
        )
    }
}
