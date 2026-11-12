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
import org.junit.Test

class SwissTest {

    private fun section(fixture: String, stage: String): StandingsSection =
        LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(fixture)).toDomainStandings()
            .stages.single { it.name == stage }.sections.single()

    private val played by lazy { section("getStandings_worlds2026_knockouts.json", "Swiss") }

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

    @Test
    fun `the played worlds swiss is drawn as five rounds grouped by record`() {
        val rounds = requireNotNull(buildSwiss(played))

        assertEquals(listOf("Round 1", "Round 2", "Round 3", "Round 4", "Round 5"), rounds.map { it.name })
        assertEquals(listOf(8, 8, 8, 6, 3), rounds.sizes)
        assertEquals(fullRecords, rounds.records())
        // The list is in round order, and every match is drawn once.
        val ids = played.matches.map { it.match.id }
        assertEquals(listOf(ids.subList(0, 8), ids.subList(8, 16), ids.subList(16, 24), ids.subList(24, 30), ids.subList(30, 33)), rounds.map { r -> r.matches.map { it.match.id } })
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
        // Rounds 4 and 5 not paired yet: the API lists them as TBD placeholders.
        val matches = played.matches.mapIndexed { i, m -> if (i >= 24) placeholder(m.match.id) else m }
        val rounds = requireNotNull(buildSwiss(played.copy(matches = matches)))

        assertEquals(listOf(8, 8, 8, 6, 3), rounds.sizes)
        assertEquals(fullRecords.take(3) + listOf(listOf(null to 6), listOf(null to 3)), rounds.records())
        assertEquals(matches.subList(24, 30), rounds[3].matches)
    }

    @Test
    fun `a round being played is grouped by the records the teams had going in`() {
        val matches = played.matches.mapIndexed { i, m ->
            when {
                i in 16 until 24 -> m.copy(state = if (i == 16) MatchState.IN_PROGRESS else MatchState.UNSTARTED, match = m.match.copy(teams = m.match.teams.map { it.copy(outcome = null, gameWins = 0) }))
                i >= 24 -> placeholder(m.match.id)
                else -> m
            }
        }
        val rounds = requireNotNull(buildSwiss(played.copy(matches = matches)))

        assertEquals(listOf(8, 8, 8, 6, 3), rounds.sizes)
        assertEquals(fullRecords.take(3) + listOf(listOf(null to 6), listOf(null to 3)), rounds.records())
    }

    @Test
    fun `rounds the api does not list yet are simply not drawn`() {
        val rounds = requireNotNull(buildSwiss(played.copy(matches = played.matches.take(16))))

        assertEquals(listOf(8, 8), rounds.sizes)
        assertEquals(fullRecords.take(2), rounds.records())
    }

    @Test
    fun `a swiss with no team known yet stays a list`() {
        assertNull(buildSwiss(section("getStandings_worlds2026.json", "Swiss")))
    }

    @Test
    fun `thirty-three matches whose teams meet with different records are not swiss`() {
        // Flipping one first-round result leaves both teams of their second-round matches with different records.
        val first = played.matches[0]
        val flipped = first.copy(match = first.match.copy(teams = first.match.teams.map { it.copy(outcome = if (it.outcome == MatchOutcome.WIN) MatchOutcome.LOSS else MatchOutcome.WIN) }))
        assertNull(buildSwiss(played.copy(matches = listOf(flipped) + played.matches.drop(1))))
    }

    @Test
    fun `thirty-three matches that are not a swiss stay a list`() {
        // 16 teams, 33 decided matches paired with no regard for records.
        val teams = (0 until 16).map { "t$it" }
        val matches = (0 until 33).map { i -> won("m$i", teams[i % 16], teams[(i * 5 + 3) % 16]) }
        assertNull(buildSwiss(StandingsSection("Swiss", emptyList(), matches)))
    }

    @Test
    fun `a team that keeps playing after three wins is not swiss`() {
        // The two teams that are 3-0 after three rounds meet in the last match instead of two 2-2 teams.
        val wins = played.matches.take(24).mapNotNull { m -> m.match.teams.singleOrNull { it.outcome == MatchOutcome.WIN }?.id }
            .groupingBy { it }.eachCount()
        val (a, b) = wins.filterValues { it == 3 }.keys.toList()
        val section = played.copy(matches = played.matches.take(32) + won("extra", a, b))
        assertNull(buildSwiss(section))
    }

    @Test
    fun `team counts whose record groups cannot pair evenly are not swiss`() {
        // 8 teams: after three rounds the 2-1 group has three teams.
        val teams = (0 until 8).map { "t$it" }
        val firstRound = (0 until 4).map { won("r$it", teams[2 * it], teams[2 * it + 1]) }
        assertNull(buildSwiss(StandingsSection("Swiss", emptyList(), firstRound)))
    }

    @Test
    fun `custom gauntlets are not swiss`() {
        assertNull(buildSwiss(lckRegionalChampionship()))
        assertNull(buildSwiss(lckPlayIns()))
    }

    @Test
    fun `ranked sections are not swiss`() {
        val ranked = played.copy(rankings = listOf(Ranking(1, team("a"), 3, 0)))
        assertNull(buildSwiss(ranked))
    }
}

internal fun team(id: String, outcome: MatchOutcome? = null, gameWins: Int? = null) =
    Team(id, id.uppercase(), null, outcome, gameWins, id = id)

internal fun won(id: String, winner: String, loser: String) = SectionMatch(
    MatchState.COMPLETED,
    Match(id, listOf(team(winner, MatchOutcome.WIN, 3), team(loser, MatchOutcome.LOSS, 1)), format = null),
)

internal fun upcoming(id: String, first: String, second: String) =
    SectionMatch(MatchState.UNSTARTED, Match(id, listOf(team(first), team(second)), format = null))

/** A match whose teams are not known yet, as the API sends it. */
internal fun placeholder(id: String): SectionMatch {
    val tbd = Team("TBD", "TBD", null, null, null, id = "0")
    return SectionMatch(MatchState.UNSTARTED, Match(id, listOf(tbd, tbd), format = null))
}

/** Shape of LCK 2026 "Regional Championship": a custom gauntlet of 6 teams and 10 matches. */
internal fun lckRegionalChampionship() = StandingsSection(
    "Regional Championship", emptyList(),
    listOf(
        won("g1", "c", "f"), won("g2", "d", "e"), won("g3", "a", "d"), won("g4", "b", "c"),
        won("g5", "c", "e"), won("g6", "d", "f"), won("g7", "a", "b"), won("g8", "c", "d"),
        won("g9", "b", "c"), won("g10", "a", "b"),
    ),
)

/** Shape of LCK 2026 "Play-Ins": 4 teams, 3 matches, the third is not winner vs winner. */
internal fun lckPlayIns() = StandingsSection(
    "Play-Ins", emptyList(),
    listOf(won("p1", "a", "b"), won("p2", "c", "d"), won("p3", "b", "c")),
)
