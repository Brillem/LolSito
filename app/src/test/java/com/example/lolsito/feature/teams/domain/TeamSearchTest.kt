package com.example.lolsito.feature.teams.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSearchTest {

    private fun team(
        id: String,
        name: String,
        code: String,
        league: String? = "LCK",
        active: Boolean = true,
        players: List<Player> = emptyList(),
    ) = DirectoryTeam(id, name, code, null, league, active, players)

    private fun player(summoner: String, first: String? = null, last: String? = null) =
        Player(summoner, summoner, first, last, null, PlayerRole.MID)

    private fun names(results: TeamSearchResults) = results.teams.map { it.name }

    @Test
    fun `teams that are not active are not searched`() {
        val teams = listOf(team("1", "Alpha", "ALP"), team("2", "Alpha Old", "ALO", active = false))

        assertEquals(listOf("Alpha"), names(searchTeamsAndPlayers(teams, "alpha")))
    }

    @Test
    fun `teams without a home league are not searched`() {
        val teams = listOf(team("1", "Alpha", "ALP"), team("2", "Alpha Free", "ALF", league = null))

        assertEquals(listOf("Alpha"), names(searchTeamsAndPlayers(teams, "alpha")))
    }

    @Test
    fun `players of teams that are not searchable are not found`() {
        val faker = player("Faker")
        val teams = listOf(team("1", "T1", "T1", active = false, players = listOf(faker)), team("2", "X", "X", league = null, players = listOf(faker)))

        assertTrue(searchTeamsAndPlayers(teams, "faker").players.isEmpty())
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("Gen.G"), names(searchTeamsAndPlayers(listOf(team("1", "Gen.G", "GEN")), "gEN.g")))
    }

    @Test
    fun `matching ignores diacritics in the query and in the data`() {
        val teams = listOf(
            team("1", "Fnatic", "FNC", players = listOf(player("Rekkles", "Martin", "Larsson"))),
            team("2", "Café Team", "CAF"),
        )

        assertEquals(listOf("Café Team"), names(searchTeamsAndPlayers(teams, "cafe")))
        assertEquals(listOf("Fnatic"), names(searchTeamsAndPlayers(teams, "fnátic")))
        assertEquals(listOf("Rekkles"), searchTeamsAndPlayers(teams, "rékkles").players.map { it.player.summonerName })
    }

    @Test
    fun `an exact code comes before names that start with or contain the query`() {
        val teams = listOf(
            team("1", "Contains Gen Inside", "CGI"),
            team("2", "Genesis", "GNS"),
            team("3", "Gen.G", "GEN"),
        )

        assertEquals(listOf("Gen.G", "Genesis", "Contains Gen Inside"), names(searchTeamsAndPlayers(teams, "gen")))
    }

    @Test
    fun `starts-with comes before contains and ties are sorted by name`() {
        val teams = listOf(
            team("1", "Zeta Dragons", "ZD"),
            team("2", "Beta Dragons", "BD"),
            team("3", "Dragons Alpha", "DA"),
            team("4", "Dragons Zeta", "DZ"),
        )

        assertEquals(
            listOf("Dragons Alpha", "Dragons Zeta", "Beta Dragons", "Zeta Dragons"),
            names(searchTeamsAndPlayers(teams, "dragons")),
        )
    }

    @Test
    fun `a team matches on its code too`() {
        val teams = listOf(team("1", "Long Name", "LNG"))

        assertEquals(listOf("Long Name"), names(searchTeamsAndPlayers(teams, "lng")))
    }

    @Test
    fun `a player match leads to the team that opens`() {
        val teams = listOf(team("1", "T1", "T1", players = listOf(player("Faker", "Sanghyeok", "Lee"))), team("2", "Gen.G", "GEN"))

        val hit = searchTeamsAndPlayers(teams, "Faker").players.single()

        assertEquals("1", hit.team.id)
        assertEquals("Faker", hit.player.summonerName)
    }

    @Test
    fun `a player matches on the real name`() {
        val teams = listOf(team("1", "T1", "T1", players = listOf(player("Faker", "Sanghyeok", "Lee"))))

        assertEquals(1, searchTeamsAndPlayers(teams, "sanghyeok lee").players.size)
        assertEquals(1, searchTeamsAndPlayers(teams, "hyeok").players.size)
    }

    @Test
    fun `players whose summoner name starts with the query come before those that contain it, ties by name`() {
        val teams = listOf(
            team("1", "A", "A1", players = listOf(player("xKeria"), player("Keria2"), player("Keria1"))),
            team("2", "B", "B1", players = listOf(player("Real", "Keria", "Smith"))),
        )

        assertEquals(
            listOf("Keria1", "Keria2", "xKeria", "Real"),
            searchTeamsAndPlayers(teams, "keria").players.map { it.player.summonerName },
        )
    }

    @Test
    fun `a query shorter than two characters after trimming finds nothing`() {
        val teams = listOf(team("1", "T1", "T1", players = listOf(player("Faker"))))

        assertTrue(searchTeamsAndPlayers(teams, "t").isEmpty)
        assertTrue(searchTeamsAndPlayers(teams, "  t  ").isEmpty)
        assertTrue(searchTeamsAndPlayers(teams, "   ").isEmpty)
        assertEquals(1, searchTeamsAndPlayers(teams, " t1 ").teams.size)
    }

    @Test
    fun `teams and players are capped`() {
        val teams = (1..30).map { i ->
            team("$i", "Team %02d".format(i), "T%02d".format(i), players = listOf(player("Team player %02d".format(i))))
        }

        val results = searchTeamsAndPlayers(teams, "team")

        assertEquals(SEARCH_RESULT_LIMIT, results.teams.size)
        assertEquals(SEARCH_RESULT_LIMIT, results.players.size)
        assertEquals(5, searchTeamsAndPlayers(teams, "team", limit = 5).teams.size)
    }
}
