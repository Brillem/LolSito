package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.feature.standings.data.toDomainStandings
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LeagueTeamsTest {

    private fun team(name: String, code: String, id: String?) = Team(name, code, "https://logo/$code", null, null, id = id)

    @Test
    fun `takes teams from rankings and section matches, once each, sorted by name, without TBD or id-less teams`() {
        val standings = Standings(
            listOf(
                StandingsStage(
                    "Groups",
                    "groups",
                    listOf(
                        StandingsSection(
                            "Group A",
                            listOf(Ranking(1, team("T1", "T1", "1"), 3, 0), Ranking(2, team("Gen.G", "GEN", "2"), 2, 1)),
                            emptyList(),
                        ),
                    ),
                ),
                StandingsStage(
                    "Playoffs",
                    "playoffs",
                    listOf(
                        StandingsSection(
                            "Final",
                            emptyList(),
                            listOf(
                                SectionMatch(MatchState.UNSTARTED, Match("m1", listOf(team("T1", "T1", "1"), team("TBD", "TBD", "0")), null)),
                                SectionMatch(MatchState.COMPLETED, Match("m2", listOf(team("BNK FEARX", "BFX", "3"), team("No id", "NID", null)), null)),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                TeamSummary("3", "BNK FEARX", "BFX", "https://logo/BFX"),
                TeamSummary("2", "Gen.G", "GEN", "https://logo/GEN"),
                TeamSummary("1", "T1", "T1", "https://logo/T1"),
            ),
            teamsOfStandings(standings),
        )
    }

    @Test
    fun `the LCK fixture lists the same ten teams as before, now partly from the bracket columns`() {
        val standings = LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read("getStandingsV3_lck.json")).toDomainStandings()

        val teams = teamsOfStandings(standings)

        // The list getStandings gave for this tournament, sorted by name.
        assertEquals(listOf("BFX", "DNS", "DK", "GEN", "BRO", "HLE", "KRX", "KT", "NS", "T1"), teams.map { it.code })
        assertEquals(teams.map { it.id }.distinct(), teams.map { it.id })
        assertEquals("98767991853197861", teams.single { it.name == "T1" }.id)
    }

    @Test
    fun `bracket-only fixtures list every team of their columns`() {
        fun teams(fixture: String) =
            teamsOfStandings(LolEsportsJson.decodeFromString<StandingsResponse>(Fixtures.read(fixture)).toDomainStandings())

        assertEquals(19, teams("getStandingsV3_worlds2026.json").size)
        assertEquals(11, teams("getStandingsV3_msi2026.json").size)
    }

    @Test
    fun `TBD slots of a bracket column are left out`() {
        val standings = LolEsportsJson.decodeFromString<StandingsResponse>(
            """{"data":{"standings":[{"stages":[{"name":"Knockouts","sections":[{"name":"Knockouts","type":"bracket","columns":[
                {"cells":[{"name":"Finals","matches":[{"id":"f","structuralId":"match_f","state":"unstarted","teams":[
                    {"id":"98767991853197861","code":"T1","name":"T1","origin":{"structuralId":"dp","type":"decisionPoint","slot":1}},
                    {"id":"0","code":"TBD","name":"TBD","origin":{"structuralId":"dp","type":"decisionPoint","slot":2}}]}]}]}
            ]}]}]}]}}"""
        ).toDomainStandings()

        assertEquals(listOf("T1"), teamsOfStandings(standings).map { it.code })
    }

    @Test
    fun `current teams come from the standings of the tournament pickDefaultTournament chooses`() = runTest {
        val requested = mutableListOf<String>()
        val repository = object : StandingsRepository {
            override suspend fun getTournaments(leagueId: String) = listOf(
                Tournament("next", "next", LocalDate.parse("2027-01-10"), LocalDate.parse("2027-04-01")),
                Tournament("now", "now", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01")),
                Tournament("old", "old", LocalDate.parse("2026-01-10"), LocalDate.parse("2026-04-01")),
            ).also { requested += "tournaments:$leagueId" }

            override suspend fun getStandings(tournamentId: String): Standings {
                requested += "standings:$tournamentId"
                return Standings(listOf(StandingsStage("S", null, listOf(StandingsSection("A", listOf(Ranking(1, team("T1", "T1", "1"), 0, 0)), emptyList())))))
            }
        }

        val teams = repository.currentTeams("lck", LocalDate.parse("2026-11-11"))

        assertEquals(listOf("tournaments:lck", "standings:now"), requested)
        assertEquals(listOf("1"), teams.map { it.id })
    }

    @Test
    fun `a league without tournaments has no teams and asks for no standings`() = runTest {
        val requested = mutableListOf<String>()
        val repository = object : StandingsRepository {
            override suspend fun getTournaments(leagueId: String) = emptyList<Tournament>()
            override suspend fun getStandings(tournamentId: String): Standings {
                requested += tournamentId
                return Standings(emptyList())
            }
        }

        assertEquals(emptyList<TeamSummary>(), repository.currentTeams("x", LocalDate.parse("2026-11-11")))
        assertEquals(emptyList<String>(), requested)
    }
}
