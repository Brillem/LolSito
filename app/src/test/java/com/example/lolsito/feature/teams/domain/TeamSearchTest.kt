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
        padRoster: Boolean = true,
    ) = DirectoryTeam(id, name, code, null, league, active, if (padRoster) withBench(id, players) else players)

    /** [players] plus bench players up to a full roster, so the team counts as searchable. */
    private fun withBench(teamId: String, players: List<Player>): List<Player> =
        players + (players.size until MIN_SEARCHABLE_ROSTER).map { Player("bench-$teamId-$it", "Bench$it", null, null, null, PlayerRole.OTHER) }

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

    @Test
    fun `teams with fewer than five players are not searched, nor are their players`() {
        val teams = listOf(
            team("1", "Alpha Full", "ALF", players = listOf(player("Ana"), player("Bo"), player("Cy"), player("Di"), player("Ed"))),
            team("2", "Alpha Four", "AL4", players = listOf(player("Ann"), player("Bob"), player("Cyd"), player("Dio")), padRoster = false),
            team("3", "Alpha Empty", "AL0", padRoster = false),
        )

        assertEquals(listOf("Alpha Full"), names(searchTeamsAndPlayers(teams, "alpha")))
        assertTrue(searchTeamsAndPlayers(teams, "al4").isEmpty)
        assertTrue(searchTeamsAndPlayers(teams, "al0").isEmpty)
        assertTrue(searchTeamsAndPlayers(teams, "ann").players.isEmpty())
        assertEquals(1, searchTeamsAndPlayers(teams, "ana").players.size)
    }

    private fun faker(id: String? = "p1") = Player(id, "Faker", "Sanghyeok", "Lee", null, PlayerRole.MID)

    @Test
    fun `a player on two teams is listed once, under the team whose league comes first`() {
        val academy = team("1", "T1 Academy", "T1A", league = "LCK Challengers", players = listOf(faker()))
        val main = team("2", "T1", "T1", league = "LCK", players = listOf(faker()))
        val order = listOf("LCS", "LCK", "LCK Challengers")

        for (teams in listOf(listOf(academy, main), listOf(main, academy))) {
            val hits = searchTeamsAndPlayers(teams, "faker", leagueOrder = order).players
            assertEquals(listOf("2"), hits.map { it.team.id })
        }
    }

    @Test
    fun `league order matches names trimmed and ignoring case`() {
        val teams = listOf(
            team("1", "B", "B1", league = "LEC", players = listOf(faker())),
            team("2", "A", "A1", league = " lck ", players = listOf(faker())),
        )

        assertEquals(listOf("2"), searchTeamsAndPlayers(teams, "faker", leagueOrder = listOf("LCK", "LEC")).players.map { it.team.id })
    }

    @Test
    fun `a team whose league is not in the order loses to a listed one, and ties keep the first team`() {
        val unknown = team("1", "A", "A1", league = "Mystery", players = listOf(faker()))
        val listed = team("2", "B", "B1", league = "LCK", players = listOf(faker()))

        assertEquals(listOf("2"), searchTeamsAndPlayers(listOf(unknown, listed), "faker", leagueOrder = listOf("LCK")).players.map { it.team.id })
        assertEquals(listOf("1"), searchTeamsAndPlayers(listOf(unknown, listed), "faker").players.map { it.team.id })
    }

    @Test
    fun `players without an id are never merged`() {
        val teams = listOf(
            team("1", "A", "A1", players = listOf(faker(id = null))),
            team("2", "B", "B1", players = listOf(faker(id = " "))),
        )

        assertEquals(2, searchTeamsAndPlayers(teams, "faker").players.size)
    }

    @Test
    fun `the cap counts distinct players`() {
        val shared = (1..4).map { Player("s$it", "Shared $it", null, null, null, PlayerRole.MID) }
        val teams = listOf(
            team("1", "A", "A1", league = "LCK Challengers", players = shared),
            team("2", "B", "B1", league = "LCK", players = shared + Player("x", "Shared 5", null, null, null, PlayerRole.MID)),
        )

        val hits = searchTeamsAndPlayers(teams, "shared", limit = 5, leagueOrder = listOf("LCK", "LCK Challengers")).players

        assertEquals(listOf("Shared 1", "Shared 2", "Shared 3", "Shared 4", "Shared 5"), hits.map { it.player.summonerName })
        assertEquals(setOf("2"), hits.map { it.team.id }.toSet())
    }
}
