package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BracketTest {

    @Test
    fun `three matches make semifinals and a final, with the final teams in any order`() {
        val section = matchSection(
            won("s1", winner = "a", loser = "b"),
            won("s2", winner = "c", loser = "d"),
            // The final lists the second semifinal's winner first.
            pending("f", "c", "a"),
        )

        val rounds = requireNotNull(buildBracket(section))

        assertEquals(listOf("Semifinals", "Final"), rounds.names)
        assertEquals(listOf(listOf("s1", "s2"), listOf("f")), rounds.ids)
    }

    @Test
    fun `seven unstarted matches make quarterfinals, semifinals and a final`() {
        val section = matchSection(*Array(7) { tbd("m$it") })

        val rounds = requireNotNull(buildBracket(section))

        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), rounds.names)
        assertEquals(listOf(listOf("m0", "m1", "m2", "m3"), listOf("m4", "m5"), listOf("m6")), rounds.ids)
    }

    @Test
    fun `fifteen matches start with a round of 16`() {
        val section = matchSection(*Array(15) { tbd("m$it") })

        val rounds = requireNotNull(buildBracket(section))

        assertEquals(listOf("Round of 16", "Quarterfinals", "Semifinals", "Final"), rounds.names)
        assertEquals(listOf(8, 4, 2, 1), rounds.map { it.matches.size })
        assertEquals((0 until 15).map { "m$it" }, rounds.flatMap { r -> r.matches.map { it.match.id } })
    }

    @Test
    fun `sections whose match count is not a single-elimination bracket stay lists`() {
        listOf(0, 1, 2, 4, 6, 8, 14, 16, 33).forEach { count ->
            assertNull("count $count", buildBracket(matchSection(*Array(count) { tbd("m$it") })))
        }
    }

    @Test
    fun `a decided match whose winner is missing from the match it feeds is not a bracket`() {
        val section = matchSection(
            won("s1", winner = "a", loser = "b"),
            won("s2", winner = "c", loser = "d"),
            pending("f", "c", "x"),
        )

        assertNull(buildBracket(section))
    }

    @Test
    fun `a winner that shows up in the wrong next-round match is not a bracket`() {
        val section = matchSection(
            won("q1", winner = "a", loser = "b"),
            tbd("q2"),
            tbd("q3"),
            tbd("q4"),
            // q1 feeds s1, but its winner is in s2.
            tbd("s1"),
            pending("s2", "a", "TBD"),
            tbd("f"),
        )

        assertNull(buildBracket(section))
    }

    @Test
    fun `a winner known only by game wins must also advance`() {
        val decidedByGames = SectionMatch(
            MatchState.COMPLETED,
            Match("s1", listOf(team("a", gameWins = 3), team("b", gameWins = 1)), format = null),
        )

        assertNull(buildBracket(matchSection(decidedByGames, tbd("s2"), pending("f", "b", "TBD"))))
        assertNotNull(buildBracket(matchSection(decidedByGames, tbd("s2"), pending("f", "a", "TBD"))))
    }

    @Test
    fun `later rounds still to be decided keep the bracket`() {
        val section = matchSection(
            won("q1", winner = "a", loser = "b"),
            won("q2", winner = "c", loser = "d"),
            won("q3", winner = "e", loser = "f"),
            pending("q4", "g", "h", MatchState.IN_PROGRESS),
            pending("s1", "c", "a"),
            pending("s2", "e", "TBD"),
            tbd("f"),
        )

        val rounds = requireNotNull(buildBracket(section))

        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), rounds.names)
    }

    @Test
    fun `a final with no teams yet keeps the bracket while the semifinals are undecided`() {
        val emptyFinal = SectionMatch(MatchState.UNSTARTED, Match("f", emptyList(), format = null))

        assertNotNull(buildBracket(matchSection(pending("s1", "a", "b"), pending("s2", "c", "d"), emptyFinal)))
    }

    @Test
    fun `teams without an id are matched by code`() {
        fun noId(code: String, outcome: MatchOutcome? = null) = Team(code, code, null, outcome, null, id = null)
        val semi = SectionMatch(MatchState.COMPLETED, Match("s1", listOf(noId("A", MatchOutcome.WIN), noId("B", MatchOutcome.LOSS)), null))
        val final = SectionMatch(MatchState.UNSTARTED, Match("f", listOf(noId("TBD"), noId("A")), null))

        assertNotNull(buildBracket(matchSection(semi, tbd("s2"), final)))
    }

    @Test
    fun `a ranked section is never a bracket`() {
        val section = StandingsSection(
            "Group A",
            rankings = listOf(Ranking(1, team("a"), 1, 0)),
            matches = List(7) { tbd("m$it") },
        )

        assertNull(buildBracket(section))
    }

    private val List<BracketRound>.names get() = map { it.name }
    private val List<BracketRound>.ids get() = map { round -> round.matches.map { it.match.id } }

    private fun matchSection(vararg matches: SectionMatch) = StandingsSection("Knockouts", emptyList(), matches.toList())

    private fun team(id: String, outcome: MatchOutcome? = null, gameWins: Int? = null) =
        Team(id, id.uppercase(), null, outcome, gameWins, id = id)

    private fun won(id: String, winner: String, loser: String) = SectionMatch(
        MatchState.COMPLETED,
        Match(id, listOf(team(winner, MatchOutcome.WIN, 3), team(loser, MatchOutcome.LOSS, 1)), format = null),
    )

    private fun pending(id: String, first: String, second: String, state: MatchState = MatchState.UNSTARTED) =
        SectionMatch(state, Match(id, listOf(team(first), team(second)), format = null))

    /** Placeholder match as the API sends it: two "TBD" teams sharing id "0". */
    private fun tbd(id: String) = SectionMatch(
        MatchState.UNSTARTED,
        Match(id, listOf(Team("TBD", "TBD", null, null, null, id = "0"), Team("TBD", "TBD", null, null, null, id = "0")), format = null),
    )
}
