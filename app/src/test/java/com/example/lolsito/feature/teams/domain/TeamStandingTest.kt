package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TeamStandingTest {

    private fun team(name: String, id: String?) = Team(name, name.take(3), null, null, null, id = id)

    private fun ranking(name: String, id: String?, ordinal: Int? = 1, wins: Int? = 0, losses: Int? = 0) =
        Ranking(ordinal, team(name, id), wins, losses)

    private fun stage(name: String, vararg rankings: Ranking) =
        StandingsStage(name, null, listOf(StandingsSection("Table", rankings.toList(), emptyList())))

    @Test
    fun `ordinals follow English rules, including the teens`() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 10 to "10th", 11 to "11th", 12 to "12th",
            13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 101 to "101st", 111 to "111th",
        )
        expected.forEach { (n, label) -> assertEquals(label, ordinalLabel(n)) }
    }

    @Test
    fun `the line has the place, the league and the record`() {
        assertEquals("3rd in LCK · 12-4", standingLine("LCK", ranking("T1", "1", ordinal = 3, wins = 12, losses = 4)))
    }

    @Test
    fun `without an ordinal the line is the league and the record`() {
        assertEquals("LCK · 12-4", standingLine("LCK", ranking("T1", "1", ordinal = null, wins = 12, losses = 4)))
    }

    @Test
    fun `without wins or losses the line is the place in the league`() {
        assertEquals("3rd in LCK", standingLine("LCK", ranking("T1", "1", ordinal = 3, wins = null, losses = null)))
        assertEquals("3rd in LCK", standingLine("LCK", ranking("T1", "1", ordinal = 3, wins = 12, losses = null)))
    }

    @Test
    fun `a ranking with neither place nor record gives no line`() {
        assertNull(standingLine("LCK", ranking("T1", "1", ordinal = null, wins = null, losses = null)))
    }

    @Test
    fun `a zero record is still a record`() {
        assertEquals("1st in LCK · 0-0", standingLine("LCK", ranking("T1", "1", ordinal = 1, wins = 0, losses = 0)))
    }

    @Test
    fun `the team's ranking is the one of the last stage that ranks it`() {
        val standings = Standings(
            listOf(
                stage("Regular season", ranking("T1", "1", ordinal = 1)),
                stage("Playoffs", ranking("T1", "1", ordinal = 3), ranking("Gen.G", "2", ordinal = 1)),
                StandingsStage("Finals", null, emptyList()),
            ),
        )

        assertEquals(3, findTeamRanking(standings, "1", "T1")?.ordinal)
    }

    @Test
    fun `the ranking is matched by id before the name`() {
        val standings = Standings(
            listOf(stage("Season", ranking("T1", "other", ordinal = 9), ranking("Renamed", "1", ordinal = 2))),
        )

        assertEquals(2, findTeamRanking(standings, "1", "T1")?.ordinal)
    }

    @Test
    fun `without an id match the ranking falls back to the name, ignoring case`() {
        val standings = Standings(listOf(stage("Season", ranking("t1 ", null, ordinal = 4), ranking("Gen.G", "2"))))

        assertEquals(4, findTeamRanking(standings, "1", "T1")?.ordinal)
    }

    @Test
    fun `a team that is not ranked, or standings without tables, have no ranking`() {
        assertNull(findTeamRanking(Standings(listOf(stage("Season", ranking("Gen.G", "2")))), "1", "T1"))
        assertNull(findTeamRanking(Standings(emptyList()), "1", "T1"))
    }

    @Test
    fun `the standing line comes from the default tournament of the league`() = runTest {
        val repository = object : StandingsRepository {
            override suspend fun getTournaments(leagueId: String) = listOf(
                Tournament("old", "old", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
                Tournament("now", "now", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1)),
            )

            override suspend fun getStandings(tournamentId: String) =
                if (tournamentId == "now") Standings(listOf(stage("Season", ranking("T1", "1", 2, 10, 5)))) else Standings(emptyList())
        }

        assertEquals("2nd in LCK · 10-5", repository.teamStandingLine("lck", "LCK", "1", "T1", LocalDate.of(2026, 7, 1)))
        assertNull(repository.teamStandingLine("lck", "LCK", "9", "Other", LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `no tournament means no line`() = runTest {
        val repository = object : StandingsRepository {
            override suspend fun getTournaments(leagueId: String) = emptyList<Tournament>()
            override suspend fun getStandings(tournamentId: String) = Standings(emptyList())
        }

        assertNull(repository.teamStandingLine("lck", "LCK", "1", "T1", LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `a finished or not started tournament has no standing line`() = runTest {
        val repository = object : StandingsRepository {
            override suspend fun getTournaments(leagueId: String) = listOf(
                Tournament("split", "split", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1)),
            )

            override suspend fun getStandings(tournamentId: String) =
                Standings(listOf(stage("Season", ranking("T1", "1", 2, 10, 5))))
        }

        assertEquals("2nd in LCK · 10-5", repository.teamStandingLine("lck", "LCK", "1", "T1", LocalDate.of(2026, 9, 1)))
        assertNull(repository.teamStandingLine("lck", "LCK", "1", "T1", LocalDate.of(2026, 9, 2)))
        assertNull(repository.teamStandingLine("lck", "LCK", "1", "T1", LocalDate.of(2026, 5, 31)))
    }

    @Test
    fun `a tournament is ongoing from its start date to its end date, or with no end date`() {
        val split = Tournament("t", "t", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1))
        assertTrue(isOngoing(split, LocalDate.of(2026, 6, 1)))
        assertTrue(isOngoing(split, LocalDate.of(2026, 9, 1)))
        assertFalse(isOngoing(split, LocalDate.of(2026, 9, 2)))
        assertFalse(isOngoing(split, LocalDate.of(2026, 5, 31)))
        assertTrue(isOngoing(split.copy(endDate = null), LocalDate.of(2027, 1, 1)))
        assertFalse(isOngoing(split.copy(startDate = null), LocalDate.of(2026, 7, 1)))
    }
}
