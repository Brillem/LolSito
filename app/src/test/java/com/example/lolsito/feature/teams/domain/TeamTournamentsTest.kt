package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.standings.domain.BracketCell
import com.example.lolsito.feature.standings.domain.BracketLayoutColumn
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.WinnerLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class TeamTournamentsTest {

    private val today = LocalDate.of(2026, 11, 16)

    /** A tournament that is over by [today], so only the standings decide its result. */
    private val finished = Tournament("t", "worlds_2026", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15))

    private fun team(name: String, id: String? = name.lowercase(), outcome: MatchOutcome? = null) =
        Team(name, name.take(3).uppercase(), null, outcome, null, id = id)

    private fun ranking(name: String, ordinal: Int?, id: String? = name.lowercase()) = Ranking(ordinal, team(name, id), 1, 1)

    /** The winner is the first team when [firstWins], the second when not, nobody while [state] is not completed. */
    private fun match(
        id: String,
        first: String,
        second: String,
        firstWins: Boolean = true,
        state: MatchState = MatchState.COMPLETED,
    ): SectionMatch {
        val decided = state == MatchState.COMPLETED
        val a = team(first, outcome = if (!decided) null else if (firstWins) MatchOutcome.WIN else MatchOutcome.LOSS)
        val b = team(second, outcome = if (!decided) null else if (firstWins) MatchOutcome.LOSS else MatchOutcome.WIN)
        return SectionMatch(state, Match(id, listOf(a, b), null))
    }

    private fun cell(name: String, vararg matches: SectionMatch) = BracketCell(name, matches.toList())

    private fun bracket(
        name: String,
        columns: List<List<BracketCell>>,
        links: List<WinnerLink> = emptyList(),
    ) = StandingsStage(
        name,
        name.lowercase(),
        listOf(
            StandingsSection(
                name = name,
                rankings = emptyList(),
                matches = columns.flatten().flatMap { it.matches },
                columns = columns.map { BracketLayoutColumn(it) },
                winnerLinks = links,
            ),
        ),
    )

    private fun table(name: String, vararg rankings: Ranking) =
        StandingsStage(name, name.lowercase(), listOf(StandingsSection("Table", rankings.toList(), emptyList())))

    private fun result(standings: Standings, name: String = "T1", id: String = "t1", tournament: Tournament = finished) =
        tournamentResult(standings, id, name, tournament, today)

    private val knockouts = bracket(
        "Knockouts",
        listOf(
            listOf(cell("Quarterfinals", match("q1", "T1", "KC"), match("q2", "BLG", "DK"))),
            listOf(cell("Semifinals", match("s1", "T1", "BLG", firstWins = false))),
            listOf(cell("Finals", match("f", "BLG", "GEN"))),
        ),
        links = listOf(WinnerLink("q1", "s1"), WinnerLink("q2", "s1"), WinnerLink("s1", "f")),
    )

    // ---- candidates -------------------------------------------------------------------------------

    private fun tournament(id: String, start: String?, end: String? = null) =
        Tournament(id, id, start?.let(LocalDate::parse), end?.let(LocalDate::parse))

    @Test
    fun `only started tournaments are candidates, newest start first, up to the count`() {
        val tournaments = listOf(
            tournament("future", "2026-12-01"),
            tournament("undated", null),
            tournament("old", "2024-01-01"),
            tournament("today", "2026-11-16"),
            tournament("mid", "2025-06-01"),
            tournament("recent", "2026-05-01"),
        )

        assertEquals(listOf("today", "recent", "mid"), startedTournaments(tournaments, today, 3).map { it.id })
        assertEquals(listOf("today"), startedTournaments(tournaments, today, 1).map { it.id })
    }

    // ---- participation ----------------------------------------------------------------------------

    @Test
    fun `a team in a ranking or in a bracket match participated`() {
        assertTrue(participated(Standings(listOf(table("Season", ranking("T1", 1)))), "t1", "T1"))
        assertTrue(participated(Standings(listOf(knockouts)), "t1", "T1"))
        assertFalse(participated(Standings(listOf(knockouts)), "other", "Other"))
        assertFalse(participated(Standings(emptyList()), "t1", "T1"))
    }

    @Test
    fun `participation falls back to the name only when no team has the id`() {
        val noIds = Standings(listOf(table("Season", ranking("t1 ", 1, id = null))))
        assertTrue(participated(noIds, "t1", "T1"))
        assertTrue(participated(Standings(listOf(table("Season", ranking("Renamed", 1, id = "t1")))), "t1", "T1"))
        assertFalse(participated(Standings(listOf(table("Season", ranking("Gen.G", 1)))), "t1", "T1"))
    }

    @Test
    fun `the team is found by id before the name, so a same-name team of another id is not it`() {
        val standings = Standings(listOf(table("Season", ranking("Renamed", 3, id = "t1"), ranking("T1", 1, id = "other"))))

        assertEquals("3rd", result(standings))
    }

    // ---- results ----------------------------------------------------------------------------------

    @Test
    fun `a tournament being played is in progress`() {
        val ongoing = finished.copy(endDate = LocalDate.of(2026, 11, 20))

        assertEquals(RESULT_IN_PROGRESS, result(Standings(listOf(knockouts)), tournament = ongoing))
        assertEquals(RESULT_IN_PROGRESS, result(Standings(emptyList()), tournament = ongoing))
    }

    @Test
    fun `the winner of the final is the champion and the loser the finalist`() {
        val standings = Standings(listOf(knockouts))

        assertEquals(RESULT_CHAMPION, result(standings, name = "BLG", id = "blg"))
        assertEquals(RESULT_FINALIST, result(standings, name = "GEN", id = "gen"))
    }

    @Test
    fun `the result of the final does not need the ids`() {
        val noIds = Standings(listOf(knockouts)).let { standings ->
            standings.copy(
                stages = standings.stages.map { stage ->
                    stage.copy(
                        sections = stage.sections.map { section ->
                            section.copy(
                                columns = section.columns.map { column ->
                                    BracketLayoutColumn(
                                        column.cells.map { c ->
                                            c.copy(matches = c.matches.map { m ->
                                                m.copy(match = m.match.copy(teams = m.match.teams.map { it.copy(id = null) }))
                                            })
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

        assertEquals(RESULT_CHAMPION, result(noIds, name = "blg", id = "x"))
    }

    @Test
    fun `a double elimination final is the match both bracket finals feed`() {
        val stage = bracket(
            "Regional Championship",
            listOf(
                listOf(cell("Upper Bracket - Finals", match("ub", "GEN", "HLE"))),
                listOf(cell("Lower Bracket - Finals", match("lb", "HLE", "T1"))),
                listOf(cell("Finals", match("gf", "GEN", "HLE"))),
            ),
            links = listOf(WinnerLink("ub", "gf"), WinnerLink("lb", "gf")),
        )
        val standings = Standings(listOf(stage))

        assertEquals(RESULT_CHAMPION, result(standings, name = "GEN", id = "gen"))
        assertEquals(RESULT_FINALIST, result(standings, name = "HLE", id = "hle"))
        assertEquals("Lower Bracket - Finals", result(standings, name = "T1", id = "t1"))
    }

    @Test
    fun `a final not completed claims nothing and the last round is shown`() {
        val stage = bracket(
            "Knockouts",
            listOf(
                listOf(cell("Semifinals", match("s1", "BLG", "T1"))),
                listOf(cell("Finals", match("f", "BLG", "GEN", state = MatchState.UNSTARTED))),
            ),
            links = listOf(WinnerLink("s1", "f")),
        )

        assertEquals("Finals", result(Standings(listOf(stage)), name = "BLG", id = "blg"))
        assertEquals("Semifinals", result(Standings(listOf(stage)), name = "T1", id = "t1"))
    }

    @Test
    fun `a completed final without a known winner claims nothing`() {
        val noWinner = SectionMatch(MatchState.COMPLETED, Match("f", listOf(team("BLG"), team("GEN")), null))
        val stage = bracket("Knockouts", listOf(listOf(cell("Finals", noWinner))))

        assertEquals("Finals", result(Standings(listOf(stage)), name = "BLG", id = "blg"))
    }

    @Test
    fun `a round named like a final but not the last one is not the final`() {
        val stage = bracket(
            "Knockouts",
            listOf(
                listOf(cell("Finals", match("f", "BLG", "GEN"))),
                listOf(cell("Rematch", match("r", "BLG", "GEN"), match("r2", "BLG", "DK"))),
            ),
        )

        assertEquals("Rematch", result(Standings(listOf(stage)), name = "BLG", id = "blg"))
    }

    @Test
    fun `a team that lost earlier in the last stage gets the round of its last match`() {
        val standings = Standings(listOf(knockouts))

        assertEquals("Semifinals", result(standings, name = "T1", id = "t1"))
        assertEquals("Quarterfinals", result(standings, name = "KC", id = "kc"))
        assertEquals("Quarterfinals", result(standings, name = "DK", id = "dk"))
    }

    @Test
    fun `a round that is only a number in an elimination last stage gives no label`() {
        val ladder = bracket(
            "Road to MSI",
            listOf(listOf(cell("Round 1", match("r1", "T1", "KC"))), listOf(cell("Round 2", match("r2", "T1", "BLG")))),
        )

        assertNull(result(Standings(listOf(ladder))))
    }

    @Test
    fun `a team out in an earlier bracket or Swiss stage gets that stage's name`() {
        val playIns = bracket("Play-Ins", listOf(listOf(cell("Finals", match("p", "PIN", "T1")))))
        val swiss = bracket(
            "Swiss",
            listOf(listOf(cell("Round 1", match("w1", "T1", "KC"))), listOf(cell("Round 5", match("w5", "T1", "SWI"), match("w6", "A", "B")))),
        )
        val standings = Standings(listOf(playIns, swiss, knockouts))

        assertEquals("Play-Ins", result(standings, name = "PIN", id = "pin"))
        assertEquals("Swiss", result(standings, name = "SWI", id = "swi"))
        assertEquals("Semifinals", result(standings, name = "T1", id = "t1"))
    }

    /** LPL Split 3 2026: a qualifier stage for other teams comes after the Playoffs, whose final is the real one. */
    private val lplSplit3 = Standings(
        listOf(
            table("Group Stage", ranking("BLG", 1), ranking("AL", 2), ranking("IG", 3), ranking("NIP", 4)),
            bracket("Playoffs Play-in", listOf(listOf(cell("Finals", match("pi1", "EDG", "NIP", firstWins = false))))),
            bracket(
                "Playoffs",
                listOf(
                    listOf(cell("Upper Bracket - Semifinals", match("ub", "BLG", "NIP"))),
                    listOf(cell("Finals", match("f", "AL", "BLG"))),
                ),
            ),
            bracket("Regional Qualifier", listOf(listOf(cell("Round 1", match("rq", "IG", "TES"))))),
        ),
    )

    @Test
    fun `a final followed by a stage neither finalist plays in still decides champion and finalist`() {
        assertEquals("Champion", result(lplSplit3, name = "AL", id = "al"))
        assertEquals("Finalist", result(lplSplit3, name = "BLG", id = "blg"))
    }

    @Test
    fun `a final whose winner plays a later stage is not the tournament's final`() {
        // EDG lost the play-in final; NIP won it and went on to the Playoffs.
        assertEquals("Playoffs Play-in", result(lplSplit3, name = "EDG", id = "edg"))
        assertEquals("Playoffs", result(lplSplit3, name = "NIP", id = "nip"))
    }

    @Test
    fun `a Swiss stage as the last stage has no final and gives no label`() {
        val swiss = bracket(
            "Swiss",
            listOf(listOf(cell("Round 4", match("a", "T1", "KC"), match("b", "A", "B"))), listOf(cell("Round 5", match("c", "T1", "DK"), match("d", "E", "F")))),
        )

        // Its rounds are bare "Round N", so only the stage name is left, which is not a result.
        assertNull(result(Standings(listOf(swiss))))
    }

    @Test
    fun `a ranked table gives the ordinal of the last stage the team is in`() {
        val standings = Standings(
            listOf(
                table("Regular Season", ranking("T1", 7), ranking("KC", 1)),
                bracket("Playoffs", listOf(listOf(cell("Semifinals", match("s", "KC", "BLG"))))),
            ),
        )

        assertEquals("7th", result(standings))
        assertEquals("Semifinals", result(standings, name = "KC", id = "kc"))
    }

    @Test
    fun `a ranking without an ordinal gives the stage name`() {
        val standings = Standings(listOf(table("Groups", ranking("T1", null))))

        assertEquals("Groups", result(standings))
    }

    @Test
    fun `a team that is nowhere in the standings has no result`() {
        assertNull(result(Standings(listOf(knockouts)), name = "Nobody", id = "nobody"))
        assertNull(result(Standings(emptyList())))
    }

    // ---- recent tournaments of a team -------------------------------------------------------------

    private class FakeRepository : StandingsRepository {
        val tournaments = mutableMapOf<String, List<Tournament>>()
        val standings = mutableMapOf<String, Standings>()
        val failingLeagues = mutableSetOf<String>()
        val failingTournaments = mutableSetOf<String>()

        override suspend fun getTournaments(leagueId: String): List<Tournament> {
            if (leagueId in failingLeagues) throw IOException("offline")
            return tournaments[leagueId].orEmpty()
        }

        override suspend fun getStandings(tournamentId: String): Standings {
            if (tournamentId in failingTournaments) throw IOException("offline")
            return standings.getValue(tournamentId)
        }
    }

    private val home = "home-league"
    private val inTable = Standings(listOf(table("Season", ranking("T1", 2))))
    private val without = Standings(listOf(table("Season", ranking("Gen.G", 1))))

    private fun repository(): FakeRepository = FakeRepository().apply {
        // Home: 4 started (only the 3 most recent count) and one still to come.
        tournaments[home] = listOf(
            tournament("h-future", "2026-12-01", "2027-02-01"),
            tournament("h4", "2026-06-01", "2026-09-01"),
            tournament("h3", "2026-01-01", "2026-04-01"),
            tournament("h2", "2025-06-01", "2025-09-01"),
            tournament("h1", "2025-01-01", "2025-04-01"),
        )
        tournaments[InternationalLeagues.WORLDS] = listOf(
            tournament("w26", "2026-10-15", "2026-11-15"),
            tournament("w25", "2025-10-14", "2025-11-09"),
            tournament("w24", "2024-10-14", "2024-11-09"),
        )
        tournaments[InternationalLeagues.MSI] = listOf(tournament("m26", "2026-04-20", "2026-05-20"))
        tournaments[InternationalLeagues.FIRST_STAND] = emptyList()
        listOf("h4", "h3", "h2", "h1", "w26", "w25", "w24", "m26").forEach { standings[it] = inTable }
    }

    @Test
    fun `the candidates are 3 home and 2 per international league, the section keeps the 5 latest`() = runTest {
        val repository = repository()

        val recent = repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = false)

        assertEquals(listOf("w26", "h4", "m26", "h3", "w25"), recent.map { it.tournament.id })
        assertEquals(listOf(InternationalLeagues.WORLDS, home, InternationalLeagues.MSI, home, InternationalLeagues.WORLDS), recent.map { it.leagueId })
        assertEquals(listOf("2nd", "2nd", "2nd", "2nd", "2nd"), recent.map { it.result })
        assertFalse(recent.any { it.tournament.id in setOf("h-future", "h2", "h1", "w24") })
    }

    @Test
    fun `only the tournaments the team played count`() = runTest {
        val repository = repository().apply {
            standings["h4"] = without
            standings["w26"] = without
        }

        val recent = repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = false)

        assertEquals(listOf("m26", "h3", "w25", "h2"), recent.map { it.tournament.id })
    }

    @Test
    fun `without a home league only the international tournaments are looked at`() = runTest {
        val recent = repository().recentTeamTournaments(null, "t1", "T1", today, hideResults = false)

        assertEquals(listOf("w26", "m26", "w25"), recent.map { it.tournament.id })
    }

    @Test
    fun `a failing request drops only its own candidates`() = runTest {
        val repository = repository().apply {
            failingLeagues += InternationalLeagues.MSI
            failingTournaments += "h4"
        }

        val recent = repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = false)

        assertEquals(listOf("w26", "h3", "w25", "h2"), recent.map { it.tournament.id })
    }

    @Test
    fun `when every request fails the list is empty`() = runTest {
        val repository = repository().apply {
            failingLeagues += listOf(home) + InternationalLeagues.ids
        }

        assertTrue(repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = false).isEmpty())
    }

    @Test
    fun `no spoilers hides the result of a finished tournament but not one in progress`() = runTest {
        val repository = repository().apply {
            tournaments[home] = listOf(
                tournament("now", "2026-11-01", "2026-12-20"),
                tournament("done", "2026-06-01", "2026-09-01"),
            )
            standings["now"] = inTable
            standings["done"] = inTable
        }

        val shown = repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = false)
        val hidden = repository.recentTeamTournaments(home, "t1", "T1", today, hideResults = true)

        assertEquals(RESULT_IN_PROGRESS, shown.first { it.tournament.id == "now" }.result)
        assertEquals("2nd", shown.first { it.tournament.id == "done" }.result)
        assertEquals(RESULT_IN_PROGRESS, hidden.first { it.tournament.id == "now" }.result)
        assertNull(hidden.first { it.tournament.id == "done" }.result)
        assertTrue(hidden.all { it.result == null || it.result == RESULT_IN_PROGRESS })
    }
}
