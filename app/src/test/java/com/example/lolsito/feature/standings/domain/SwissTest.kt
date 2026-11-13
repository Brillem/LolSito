package com.example.lolsito.feature.standings.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwissTest {

    private fun section(fixture: String, stage: String): StandingsSection =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(fixture)).toDomainStandings()
            .stages.single { it.name == stage }.sections.single()

    private val played by lazy { section("getStandingsV3_worlds2026.json", "Swiss") }

    /** The matches of each official round (column) of the played Worlds Swiss. */
    private val playedRounds by lazy { played.columns.map { it.cells.single().matches } }

    private val List<SwissRound>.sizes get() = map { it.matches.size }

    private fun List<SwissRound>.records(): List<List<Pair<String?, Int>>> =
        map { round -> round.groups.map { g -> g.record?.let { "${it.wins}-${it.losses}" } to g.matches.size } }

    private val fullRecords = listOf(
        listOf("0-0" to 8),
        listOf("1-0" to 4, "0-1" to 4),
        listOf("2-0" to 2, "1-1" to 4, "0-2" to 2),
        listOf("2-1" to 3, "1-2" to 3),
        listOf("2-2" to 3),
    )

    /** Every round shows its matches in API order under no record. */
    private fun List<SwissRound>.assertRoundsOnly(rounds: List<List<SectionMatch>>) {
        assertEquals(rounds, map { it.matches })
        forEach { round -> assertEquals(listOf(SwissGroup(null, round.matches)), round.groups) }
    }

    @Test
    fun `the played worlds swiss takes its five rounds from the api and groups them by record`() {
        val rounds = requireNotNull(buildSwiss(played))

        assertEquals(listOf("Round 1", "Round 2", "Round 3", "Round 4", "Round 5"), rounds.map { it.name })
        assertEquals(listOf(8, 8, 8, 6, 3), rounds.sizes)
        assertEquals(fullRecords, rounds.records())
        assertEquals(playedRounds, rounds.map { it.matches })
        rounds.forEach { round -> assertEquals(round.matches.toSet(), round.groups.flatMap { it.matches }.toSet()) }
    }

    @Test
    fun `both teams of every grouped match share the record of their group`() {
        val rounds = requireNotNull(buildSwiss(played))
        val wins = mutableMapOf<String, Int>()
        val losses = mutableMapOf<String, Int>()
        rounds.forEach { round ->
            round.groups.forEach { group ->
                val record = requireNotNull(group.record)
                group.matches.forEach { m ->
                    m.match.teams.forEach { t ->
                        assertEquals(record.wins, wins[t.id] ?: 0)
                        assertEquals(record.losses, losses[t.id] ?: 0)
                    }
                }
            }
            round.matches.forEach { m ->
                m.match.teams.forEach { t ->
                    val map = if (t.outcome == MatchOutcome.WIN) wins else losses
                    map[t.id!!] = (map[t.id] ?: 0) + 1
                }
            }
        }
    }

    @Test
    fun `later rounds still to be paired keep the played rounds and wait under no record`() {
        // Rounds 4 and 5 not paired yet: their slots are TBD placeholders.
        val rounds = playedRounds.mapIndexed { r, matches -> if (r >= 3) matches.map { placeholder(it.match.id) } else matches }
        val swiss = requireNotNull(buildSwiss(swissSection(rounds)))

        assertEquals(listOf(8, 8, 8, 6, 3), swiss.sizes)
        assertEquals(fullRecords.take(3) + listOf(listOf(null to 6), listOf(null to 3)), swiss.records())
        assertEquals(rounds[3], swiss[3].matches)
    }

    @Test
    fun `a round being played is grouped by the records the teams had going in`() {
        val rounds = playedRounds.mapIndexed { r, matches ->
            when {
                r == 2 -> matches.mapIndexed { i, m ->
                    m.copy(state = if (i == 0) MatchState.IN_PROGRESS else MatchState.UNSTARTED, match = m.match.copy(teams = m.match.teams.map { it.copy(outcome = null, gameWins = 0) }))
                }
                r >= 3 -> matches.map { placeholder(it.match.id) }
                else -> matches
            }
        }
        val swiss = requireNotNull(buildSwiss(swissSection(rounds)))

        assertEquals(listOf(8, 8, 8, 6, 3), swiss.sizes)
        assertEquals(fullRecords.take(3) + listOf(listOf(null to 6), listOf(null to 3)), swiss.records())
    }

    @Test
    fun `only the rounds the api lists are drawn`() {
        val swiss = requireNotNull(buildSwiss(swissSection(playedRounds.take(2))))

        assertEquals(listOf(8, 8), swiss.sizes)
        assertEquals(fullRecords.take(2), swiss.records())
    }

    @Test
    fun `a swiss with no team known yet shows its rounds without records`() {
        val rounds = playedRounds.map { matches -> matches.map { placeholder(it.match.id) } }

        requireNotNull(buildSwiss(swissSection(rounds))).assertRoundsOnly(rounds)
    }

    @Test
    fun `teams that meet with different records leave the rounds without records`() {
        // Flipping one first-round result leaves both teams of their second-round matches with different records.
        val first = playedRounds[0][0]
        val flipped = first.copy(match = first.match.copy(teams = first.match.teams.map { it.copy(outcome = if (it.outcome == MatchOutcome.WIN) MatchOutcome.LOSS else MatchOutcome.WIN) }))
        val rounds = listOf(listOf(flipped) + playedRounds[0].drop(1)) + playedRounds.drop(1)

        requireNotNull(buildSwiss(swissSection(rounds))).assertRoundsOnly(rounds)
    }

    @Test
    fun `rounds paired with no regard for records show no records`() {
        // 16 teams, five rounds of decided matches whose pairings ignore records.
        val teams = (0 until 16).map { "t$it" }
        val rounds = listOf(8, 8, 8, 6, 3).mapIndexed { r, size -> (0 until size).map { i -> won("m$r-$i", teams[(2 * i + r) % 16], teams[(2 * i + 5 * r + 1) % 16]) } }

        requireNotNull(buildSwiss(swissSection(rounds))).assertRoundsOnly(rounds)
    }

    @Test
    fun `a team that keeps playing after three wins leaves the rounds without records`() {
        // The two teams that are 3-0 after three rounds meet in the last round instead of two 2-2 teams.
        val wins = playedRounds.take(3).flatten().mapNotNull { m -> m.match.teams.singleOrNull { it.outcome == MatchOutcome.WIN }?.id }
            .groupingBy { it }.eachCount()
        val (a, b) = wins.filterValues { it == 3 }.keys.toList()
        val rounds = playedRounds.take(4) + listOf(playedRounds[4].take(2) + won("extra", a, b))

        requireNotNull(buildSwiss(swissSection(rounds))).assertRoundsOnly(rounds)
    }

    @Test
    fun `team counts whose record groups cannot pair evenly show no records`() {
        // 8 teams: after three rounds the 2-1 group has three teams.
        val teams = (0 until 8).map { "t$it" }
        val rounds = listOf((0 until 4).map { won("r$it", teams[2 * it], teams[2 * it + 1]) })

        requireNotNull(buildSwiss(swissSection(rounds))).assertRoundsOnly(rounds)
    }

    @Test
    fun `brackets whose matches feed each other are not swiss`() {
        val lck = LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read("getStandingsV3_lck.json")).toDomainStandings()
        assertNull(buildSwiss(lck.stages.single { it.name == "Regional Championship" }.sections.single()))
        assertNull(buildSwiss(lck.stages.single { it.name == "Play-Ins" }.sections.single()))
    }

    @Test
    fun `sections without columns and ranked sections are not swiss`() {
        assertNull(buildSwiss(played.copy(columns = emptyList())))
        assertNull(buildSwiss(played.copy(rankings = listOf(Ranking(1, team("a"), 3, 0)))))
        assertTrue(played.winnerLinks.isEmpty())
    }
}

/** A section laid out as [rounds], one column and cell per round, like the API sends a Swiss stage. */
internal fun swissSection(rounds: List<List<SectionMatch>>) = StandingsSection(
    "Swiss",
    emptyList(),
    rounds.flatten(),
    columns = rounds.mapIndexed { i, matches -> BracketLayoutColumn(listOf(BracketCell("Round ${i + 1}", matches))) },
)

internal fun team(id: String, outcome: MatchOutcome? = null, gameWins: Int? = null) =
    Team(id, id.uppercase(), null, outcome, gameWins, id = id)

internal fun won(id: String, winner: String, loser: String) = SectionMatch(
    MatchState.COMPLETED,
    Match(id, listOf(team(winner, MatchOutcome.WIN, 3), team(loser, MatchOutcome.LOSS, 1)), format = null),
)

/** A match whose teams are not known yet, as the API sends it. */
internal fun placeholder(id: String): SectionMatch {
    val tbd = Team("TBD", "TBD", null, null, null, id = "0")
    return SectionMatch(MatchState.UNSTARTED, Match(id, listOf(tbd, tbd), format = null))
}
