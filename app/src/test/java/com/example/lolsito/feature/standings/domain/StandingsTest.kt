package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingsTest {

    @Test
    fun `stage with any ranked section is not an elimination stage`() {
        val stage = StandingsStage(
            name = "Groups",
            slug = "groups",
            sections = listOf(section("A", rankings = listOf(ranking(1, "T1"))), section("B")),
        )
        assertFalse(stage.isElimination)
    }

    @Test
    fun `stage whose sections all lack rankings is an elimination stage`() {
        val stage = StandingsStage("Swiss", "swiss", listOf(section("Swiss", matches = listOf(match("m1")))))
        assertTrue(stage.isElimination)
    }

    @Test
    fun `stage without sections is not an elimination stage`() {
        assertFalse(StandingsStage("Knockouts", "knockouts", emptyList()).isElimination)
    }

    @Test
    fun `mixed stage with a ranked section and a bracket section is not an elimination stage`() {
        val stage = StandingsStage(
            "Groups", "groups",
            listOf(section("A", rankings = listOf(ranking(1, "T1"))), section("Bracket", matches = listOf(match("m1")))),
        )
        assertFalse(stage.isElimination)
    }

    @Test
    fun `unstarted stage without rankings or matches is not an elimination stage`() {
        val stage = StandingsStage("Groups", "groups", listOf(section("A"), section("B")))
        assertFalse(stage.isElimination)
    }

    @Test
    fun `section kinds follow its rankings and matches`() {
        assertTrue(section("A", rankings = listOf(ranking(1, "T1")), matches = listOf(match("m1"))).isRanked)
        assertFalse(section("A", rankings = listOf(ranking(1, "T1")), matches = listOf(match("m1"))).isMatchOnly)
        assertTrue(section("B", matches = listOf(match("m1"))).isMatchOnly)
        assertFalse(section("C").isRanked)
        assertFalse(section("C").isMatchOnly)
    }

    private fun section(name: String, rankings: List<Ranking> = emptyList(), matches: List<SectionMatch> = emptyList()) =
        StandingsSection(name, rankings, matches)

    private fun ranking(ordinal: Int, code: String) =
        Ranking(ordinal, Team(code, code, null, null, null, id = code), wins = 1, losses = 0)

    private fun match(id: String) = SectionMatch(MatchState.UNSTARTED, Match(id, emptyList(), format = null))
}
