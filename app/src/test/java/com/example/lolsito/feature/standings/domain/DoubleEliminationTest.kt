package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DoubleEliminationTest {

    private fun section(fixture: String, stage: String): StandingsSection =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(fixture)).toDomainStandings()
            .stages.single { it.name == stage }.sections.single()

    private val knockouts by lazy { section("getStandings_msi2026.json", "Knockouts") }
    private val playInKnockouts by lazy { section("getStandings_msi2026.json", "Play-Ins") }

    private val List<BracketRound>.sizes get() = map { it.matches.size }
    private val List<BracketRound>.names get() = map { it.name }

    private fun SectionMatch.winnerId(): String? = match.teams.singleOrNull { it.outcome == MatchOutcome.WIN }?.id
    private fun SectionMatch.loserId(): String? = match.teams.singleOrNull { it.outcome == MatchOutcome.LOSS }?.id
    private fun SectionMatch.teamIds(): Set<String> = match.teams.mapNotNull { it.id }.toSet()

    /** Every decided result flows where a double-elimination bracket sends it. */
    private fun assertFlow(bracket: DoubleEliminationBracket) {
        val upper = bracket.upper
        val lower = bracket.lower
        // Upper bracket: match m of a round is fed by matches 2m and 2m+1 of the previous one.
        upper.zipWithNext().forEach { (round, next) ->
            assertTrue(round.linkedToNext)
            next.matches.forEachIndexed { m, match ->
                assertEquals(setOf(round.matches[2 * m].winnerId(), round.matches[2 * m + 1].winnerId()), match.teamIds())
            }
        }
        // Lower round 1: the losers of upper round 1.
        assertEquals(upper.first().matches.map { it.loserId() }.toSet(), lower.first().matches.flatMap { it.teamIds() }.toSet())
        // Each later lower round: winners of the previous lower round, plus the losers of one upper round when it is
        // the same size (drop-in), or paired among themselves when it halves.
        var upperRound = 1
        lower.zipWithNext().forEach { (round, next) ->
            assertTrue(round.linkedToNext)
            if (next.matches.size == round.matches.size) {
                val dropped = upper[upperRound++].matches.map { it.loserId() }.toSet()
                next.matches.forEachIndexed { m, match ->
                    val fromLower = round.matches[m].winnerId()
                    assertTrue(fromLower in match.teamIds())
                    assertTrue((match.teamIds() - fromLower).single() in dropped)
                }
            } else {
                next.matches.forEachIndexed { m, match ->
                    assertEquals(setOf(round.matches[2 * m].winnerId(), round.matches[2 * m + 1].winnerId()), match.teamIds())
                }
            }
        }
        assertEquals(upper.size, upperRound)
        assertEquals(setOf(upper.last().matches.single().winnerId(), lower.last().matches.single().winnerId()), bracket.grandFinal.matches.single().teamIds())
        assertEquals("Grand Final", bracket.grandFinal.name)
    }

    private fun DoubleEliminationBracket.allIds(): List<String> =
        (upper + lower + grandFinal).flatMap { r -> r.matches.map { it.match.id } }

    @Test
    fun `eight teams make an upper bracket, a lower bracket and a grand final`() {
        val bracket = requireNotNull(buildDoubleElimination(knockouts))

        assertEquals(listOf("Upper Quarterfinals", "Upper Semifinals", "Upper Final"), bracket.upper.names)
        assertEquals(listOf(4, 2, 1), bracket.upper.sizes)
        assertEquals(listOf("Lower Round 1", "Lower Round 2", "Lower Round 3", "Lower Final"), bracket.lower.names)
        assertEquals(listOf(2, 2, 1, 1), bracket.lower.sizes)
        assertEquals(knockouts.matches.map { it.match.id }.sorted(), bracket.allIds().sorted())
        assertFlow(bracket)
    }

    @Test
    fun `four teams listed out of play order are placed by their results`() {
        val bracket = requireNotNull(buildDoubleElimination(playInKnockouts))

        assertEquals(listOf("Upper Semifinals", "Upper Final"), bracket.upper.names)
        assertEquals(listOf(2, 1), bracket.upper.sizes)
        assertEquals(listOf("Lower Round 1", "Lower Final"), bracket.lower.names)
        assertEquals(listOf(1, 1), bracket.lower.sizes)
        // The first lower-bracket match is the last one listed.
        assertEquals(playInKnockouts.matches.last().match.id, bracket.lower.first().matches.single().match.id)
        assertEquals(playInKnockouts.matches.map { it.match.id }.sorted(), bracket.allIds().sorted())
        assertFlow(bracket)
    }

    @Test
    fun `a rematch between the same teams goes to the round it was played in`() {
        // The upper semifinal and the grand final of this section are the same pairing with the same winner;
        // the earlier match (lower id) is the semifinal.
        val bracket = requireNotNull(buildDoubleElimination(playInKnockouts))
        val final = bracket.grandFinal.matches.single()
        val semi = bracket.upper.first().matches.single { it.teamIds() == final.teamIds() }
        assertTrue(semi.match.id.toBigInteger() < final.match.id.toBigInteger())
    }

    @Test
    fun `shuffled matches give the same bracket`() {
        listOf(knockouts, playInKnockouts).forEach { section ->
            val expected = buildDoubleElimination(section)
            repeat(20) { seed ->
                val shuffled = section.copy(matches = section.matches.shuffled(Random(seed)))
                assertEquals("seed $seed", expected, buildDoubleElimination(shuffled))
            }
        }
    }

    @Test
    fun `the worlds play-ins are a four-team double elimination, played or not`() {
        assertFlow(requireNotNull(buildDoubleElimination(section("getStandings_worlds2026_knockouts.json", "Play-Ins"))))

        // Two matches played, the next two set, the lower final and the grand final still TBD.
        val unplayed = section("getStandings_worlds2026.json", "Play-Ins")
        val bracket = requireNotNull(buildDoubleElimination(unplayed))
        val ids = unplayed.matches.map { it.match.id }
        assertEquals(listOf(listOf(ids[0], ids[1]), listOf(ids[2])), bracket.upper.map { r -> r.matches.map { it.match.id } })
        assertEquals(listOf(listOf(ids[3]), listOf(ids[4])), bracket.lower.map { r -> r.matches.map { it.match.id } })
        assertEquals(listOf(ids[5]), bracket.grandFinal.matches.map { it.match.id })
    }

    @Test
    fun `later matches still to be decided keep the rounds already played`() {
        // From the upper final on, nothing is played: the next lower round is set, the rest is TBD.
        val full = requireNotNull(buildDoubleElimination(knockouts))
        val upperFinal = full.upper[2].matches.single().match.id
        val lower2 = full.lower[1].matches.map { it.match.id }
        val later = listOf(upperFinal, full.lower[2].matches.single().match.id, full.lower[3].matches.single().match.id, full.grandFinal.matches.single().match.id)
        val matches = knockouts.matches.map { m ->
            when (m.match.id) {
                in later -> placeholder(m.match.id)
                in lower2 -> m.copy(state = MatchState.UNSTARTED, match = m.match.copy(teams = m.match.teams.map { it.copy(outcome = null, gameWins = 0) }))
                else -> m
            }
        }

        val bracket = requireNotNull(buildDoubleElimination(knockouts.copy(matches = matches)))

        fun List<BracketRound>.ids() = map { r -> r.matches.map { it.match.id } }
        assertEquals(listOf(4, 2, 1), bracket.upper.sizes)
        assertEquals(listOf(2, 2, 1, 1), bracket.lower.sizes)
        assertEquals(full.upper.take(2).ids(), bracket.upper.take(2).ids())
        assertTrue(bracket.upper[0].linkedToNext)
        // The upper final is not known yet, so nothing links the semifinals to it.
        assertFalse(bracket.upper[1].linkedToNext)
        assertEquals(full.lower[0], bracket.lower[0])
        assertEquals(lower2, bracket.lower[1].matches.map { it.match.id })
        // Placeholders fill the rounds still unknown, lowest id first.
        assertEquals(later, (bracket.upper.drop(2) + bracket.lower.drop(2) + bracket.grandFinal).map { it.matches.single().match.id })
    }

    @Test
    fun `a bracket with only its first round known stays a list`() {
        val section = StandingsSection(
            "Play-Ins", emptyList(),
            listOf(upcoming("a1", "a", "b"), upcoming("a2", "c", "d")) + (1..4).map { placeholder("t$it") },
        )
        assertNull(buildDoubleElimination(section))
    }

    @Test
    fun `custom gauntlets stay lists`() {
        assertNull(buildDoubleElimination(lckRegionalChampionship()))
        assertNull(buildDoubleElimination(lckPlayIns()))
    }

    @Test
    fun `a four-team round robin is not a double elimination`() {
        val section = StandingsSection(
            "Group", emptyList(),
            listOf(won("r1", "a", "b"), won("r2", "c", "d"), won("r3", "a", "c"), won("r4", "b", "d"), won("r5", "a", "d"), won("r6", "c", "b")),
        )
        assertNull(buildDoubleElimination(section))
    }

    @Test
    fun `a lower-bracket winner that does not advance is not a double elimination`() {
        val matches = playInKnockouts.matches.toMutableList()
        val bracket = requireNotNull(buildDoubleElimination(playInKnockouts))
        val lowerFirst = bracket.lower.first().matches.single()
        // Flip the first lower-bracket result: its winner no longer meets the upper-final loser.
        matches[matches.indexOf(lowerFirst)] = lowerFirst.copy(
            match = lowerFirst.match.copy(teams = lowerFirst.match.teams.map { it.copy(outcome = if (it.outcome == MatchOutcome.WIN) MatchOutcome.LOSS else MatchOutcome.WIN) }),
        )
        assertNull(buildDoubleElimination(playInKnockouts.copy(matches = matches)))
    }

    @Test
    fun `other sizes, a bracket reset and ranked sections stay lists`() {
        // A grand-final rematch (bracket reset) adds a seventh match: not supported.
        val reset = playInKnockouts.copy(matches = playInKnockouts.matches + won("reset", "x", "y"))
        assertNull(buildDoubleElimination(reset))
        listOf(0, 3, 7, 13, 15).forEach { count ->
            assertNull("count $count", buildDoubleElimination(StandingsSection("s", emptyList(), List(count) { placeholder("m$it") })))
        }
        assertNull(buildDoubleElimination(knockouts.copy(rankings = listOf(Ranking(1, team("a"), 1, 0)))))
    }

    @Test
    fun `the single-elimination bracket is unchanged and not a double elimination`() {
        val worlds = section("getStandings_worlds2026_knockouts.json", "Knockouts")
        assertNull(buildDoubleElimination(worlds))
        assertEquals(listOf(4, 2, 1), requireNotNull(buildBracket(worlds)).sizes)
        assertFalse(requireNotNull(buildBracket(worlds)).any { !it.linkedToNext })
    }
}
