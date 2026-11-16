package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.HomeLeagueDto
import com.example.lolsito.core.network.dto.PlayerDto
import com.example.lolsito.core.network.dto.TeamDetailDto
import com.example.lolsito.core.network.dto.TeamsData
import com.example.lolsito.core.network.dto.TeamsResponse
import com.example.lolsito.feature.teams.domain.PlayerRole
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamsMappersTest {

    private val t1 = LolEsportsJson.decodeFromString<TeamsResponse>(Fixtures.read("getTeams_t1.json")).toTeamProfile()!!

    @Test
    fun `maps the team header with an https logo and its home league`() {
        assertEquals("98767991853197861", t1.id)
        assertEquals("t1", t1.slug)
        assertEquals("T1", t1.name)
        assertEquals("T1", t1.code)
        assertEquals("https://static.lolesports.com/teams/1726801573959_539px-T1_2019_full_allmode.png", t1.imageUrl)
        assertEquals("LCK", t1.homeLeagueName)
        assertEquals("KOREA", t1.homeLeagueRegion)
    }

    @Test
    fun `groups every player by role in lane order, keeping the API order inside a role`() {
        assertEquals(
            listOf(PlayerRole.TOP, PlayerRole.JUNGLE, PlayerRole.MID, PlayerRole.BOTTOM, PlayerRole.SUPPORT),
            t1.roster.map { it.role },
        )
        assertEquals(
            listOf(
                listOf("Guardian", "Haetae", "Doran"),
                listOf("Carim", "Oner", "Painter"),
                listOf("Guti", "Faker"),
                listOf("Cypher", "Eclipse", "Jinbeom", "Peyz"),
                listOf("Cloud", "Keria"),
            ),
            t1.roster.map { group -> group.players.map { it.summonerName } },
        )
        assertEquals(14, t1.roster.sumOf { it.players.size })
        t1.roster.forEach { group -> group.players.forEach { assertEquals(group.role, it.role) } }
    }

    @Test
    fun `player photos are https and the real name is kept`() {
        val faker = t1.roster.flatMap { it.players }.single { it.summonerName == "Faker" }
        assertEquals("98767991747728851", faker.id)
        assertEquals("Sanghyeok", faker.firstName)
        assertEquals("Lee", faker.lastName)
        assertEquals("https://static.lolesports.com/players/1769087499078_LCK_T1_Faker_F.PNG", faker.imageUrl)
        assertTrue(t1.roster.flatMap { it.players }.all { it.imageUrl!!.startsWith("https://") })
    }

    @Test
    fun `an unknown or missing role goes to a last Other group instead of being dropped`() {
        val response = TeamsResponse(
            TeamsData(
                listOf(
                    TeamDetailDto(
                        id = "1",
                        name = "Team",
                        code = "TM",
                        players = listOf(
                            PlayerDto(id = "a", summonerName = "Coach", role = "none"),
                            PlayerDto(id = "b", summonerName = "Mid", role = "MID"),
                            PlayerDto(id = "c", summonerName = "Nobody", role = null),
                        ),
                    ),
                ),
            ),
        )

        val roster = response.toTeamProfile()!!.roster

        assertEquals(listOf(PlayerRole.MID, PlayerRole.OTHER), roster.map { it.role })
        assertEquals(listOf("Coach", "Nobody"), roster.last().players.map { it.summonerName })
    }

    @Test
    fun `no team or a team without id maps to null`() {
        assertNull(TeamsResponse().toTeamProfile())
        assertNull(TeamsResponse(TeamsData(listOf(TeamDetailDto(id = " ", name = "X")))).toTeamProfile())
    }

    @Test
    fun `a team without players has an empty roster`() {
        val profile = TeamsResponse(TeamsData(listOf(TeamDetailDto(id = "1", name = "X", code = "X")))).toTeamProfile()!!

        assertEquals(emptyList<Any>(), profile.roster)
        assertNull(profile.homeLeagueName)
    }

    @Test
    fun `the all-teams mapping keeps status and home league, and drops teams without an id`() {
        val response = TeamsResponse(
            TeamsData(
                listOf(
                    TeamDetailDto(
                        id = "1",
                        name = " T1 ",
                        code = "T1",
                        image = "http://x/t1.png",
                        status = "active",
                        homeLeague = HomeLeagueDto("LCK", "KOREA"),
                        players = listOf(PlayerDto(id = "p", summonerName = "Faker", role = "mid")),
                    ),
                    TeamDetailDto(id = "2", name = "Old", code = "OLD", status = "archived"),
                    TeamDetailDto(id = "3", name = "No status", code = "NS"),
                    TeamDetailDto(id = " ", name = "No id", status = "active"),
                    TeamDetailDto(name = "Missing id", status = "active"),
                ),
            ),
        )

        val teams = response.toDirectoryTeams()

        assertEquals(listOf("1", "2", "3"), teams.map { it.id })
        assertEquals(listOf(true, false, false), teams.map { it.active })
        val t1 = teams.first()
        assertEquals("T1", t1.name)
        assertEquals("https://x/t1.png", t1.imageUrl)
        assertEquals("LCK", t1.homeLeagueName)
        assertEquals(listOf("Faker"), t1.players.map { it.summonerName })
        assertNull(teams[1].homeLeagueName)
    }
}
