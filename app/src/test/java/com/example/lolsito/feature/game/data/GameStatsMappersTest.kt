package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.DragonType
import com.example.lolsito.feature.game.domain.PlayerHealth
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStatsMappersTest {

    private fun decode(json: String) = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(json)

    private val stats = decode(Fixtures.read("livestats_window.json")).toGameStats()!!

    @Test
    fun `maps team totals of the last frame`() {
        assertEquals(
            TeamStats(totalGold = 31850, kills = 2, towers = 0, dragons = 1, barons = 0, players = stats.blueTeam.players, dragonTypes = listOf(DragonType.CLOUD)),
            stats.blueTeam,
        )
        assertEquals(
            TeamStats(totalGold = 33191, kills = 3, towers = 2, dragons = 1, barons = 0, players = stats.redTeam.players, dragonTypes = listOf(DragonType.HEXTECH)),
            stats.redTeam,
        )
        assertFalse(stats.isFinished)
    }

    @Test
    fun `joins each team's players with their metadata by participant id`() {
        assertEquals(
            PlayerStats(1, "MVK Kratos", "Olaf", kills = 0, deaths = 2, assists = 0, creepScore = 142, level = 12, role = "top", health = PlayerHealth(2651, 2651)),
            stats.blueTeam.players.first(),
        )
        assertEquals(
            listOf("MVK Kratos", "MVK Gury", "MVK Chika", "MVK Harky", "MVK SiuLoong"),
            stats.blueTeam.players.map { it.summonerName },
        )
        assertEquals(
            listOf("Jax", "Trundle", "Galio", "Yunara", "Lulu"),
            stats.redTeam.players.map { it.championId },
        )
        assertEquals(
            PlayerStats(9, "FUR Ayu", "Yunara", kills = 1, deaths = 2, assists = 0, creepScore = 155, level = 10, role = "bottom", health = PlayerHealth(1506, 1506)),
            stats.redTeam.players[3],
        )
    }

    @Test
    fun `maps the team ids and each player's role from the feed metadata`() {
        assertEquals("107251245690956393", stats.blueTeamId)
        assertEquals("100205576309502431", stats.redTeamId)
        assertEquals(listOf("top", "jungle", "mid", "bottom", "support"), stats.blueTeam.players.map { it.role })
        assertEquals(listOf("top", "jungle", "mid", "bottom", "support"), stats.redTeam.players.map { it.role })
    }

    @Test
    fun `missing team ids and roles stay null`() {
        val stats = decode("""{"frames":[{"blueTeam":{"participants":[{"participantId":1}]}}]}""").toGameStats()!!

        assertNull(stats.blueTeamId)
        assertNull(stats.redTeamId)
        assertNull(stats.blueTeam.players.single().role)
    }

    @Test
    fun `no frames means no stats`() {
        assertNull(decode("""{"esportsGameId":"1","frames":[]}""").toGameStats())
        assertNull(decode("""{"esportsGameId":"1"}""").toGameStats())
    }

    @Test
    fun `uses the last frame and flags a finished game`() {
        val json = """
            {"frames":[
              {"gameState":"in_game","blueTeam":{"totalKills":1},"redTeam":{"totalKills":0}},
              {"gameState":"finished","blueTeam":{"totalKills":11},"redTeam":{"totalKills":10}}
            ]}
        """
        val stats = decode(json).toGameStats()!!

        assertTrue(stats.isFinished)
        assertEquals(11, stats.blueTeam.kills)
        assertEquals(10, stats.redTeam.kills)
    }

    @Test
    fun `missing numbers default to zero and a missing team is empty`() {
        val stats = decode("""{"frames":[{"blueTeam":{"participants":[{"participantId":1}]}}]}""").toGameStats()!!

        assertEquals(TeamStats(0, 0, 0, 0, 0, players = stats.blueTeam.players), stats.blueTeam)
        assertEquals(PlayerStats(1, "Player 1", "Unknown", 0, 0, 0, 0, 0), stats.blueTeam.players.single())
        assertEquals(TeamStats(0, 0, 0, 0, 0, emptyList()), stats.redTeam)
    }

    @Test
    fun `players are ordered by participant id, matched only within their team, and need an id`() {
        val json = """
            {"gameMetadata":{
               "blueTeamMetadata":{"participantMetadata":[
                 {"participantId":2,"summonerName":"B2","championId":"Vi"},
                 {"participantId":1,"summonerName":"B1","championId":"Olaf"}]},
               "redTeamMetadata":{"participantMetadata":[
                 {"participantId":6,"summonerName":"R6","championId":"Jax"}]}},
             "frames":[{"blueTeam":{"participants":[
                 {"participantId":2,"kills":3},
                 {"kills":9},
                 {"participantId":1,"kills":1},
                 {"participantId":6,"kills":4}]}}]}
        """
        val players = decode(json).toGameStats()!!.blueTeam.players

        assertEquals(listOf(1, 2, 6), players.map { it.participantId })
        assertEquals(listOf("B1", "B2", "Player 6"), players.map { it.summonerName })
        assertEquals(listOf("Olaf", "Vi", "Unknown"), players.map { it.championId })
        assertEquals(listOf(1, 3, 4), players.map { it.kills })
    }

    @Test
    fun `blank names fall back like missing ones`() {
        val json = """
            {"gameMetadata":{"blueTeamMetadata":{"participantMetadata":[
               {"participantId":1,"summonerName":" ","championId":""}]}},
             "frames":[{"blueTeam":{"participants":[{"participantId":1}]}}]}
        """
        val player = decode(json).toGameStats()!!.blueTeam.players.single()

        assertEquals("Player 1", player.summonerName)
        assertEquals("Unknown", player.championId)
    }

    @Test
    fun `maps each team's dragon types in the order taken`() {
        assertEquals(listOf(DragonType.CLOUD), stats.blueTeam.dragonTypes)
        assertEquals(listOf(DragonType.HEXTECH), stats.redTeam.dragonTypes)

        val json = """
            {"frames":[{"blueTeam":{"dragons":["mountain","infernal","ocean","chemtech","cloud","hextech","elder"]}}]}
        """
        assertEquals(
            listOf(
                DragonType.MOUNTAIN, DragonType.INFERNAL, DragonType.OCEAN, DragonType.CHEMTECH,
                DragonType.CLOUD, DragonType.HEXTECH, DragonType.ELDER,
            ),
            decode(json).toGameStats()!!.blueTeam.dragonTypes,
        )
    }

    @Test
    fun `an unknown dragon type is kept in its place as a generic dragon`() {
        val team = decode("""{"frames":[{"blueTeam":{"dragons":["ocean","voidborn","infernal"]}}]}""").toGameStats()!!.blueTeam

        assertEquals(listOf(DragonType.OCEAN, DragonType.UNKNOWN, DragonType.INFERNAL), team.dragonTypes)
    }

    @Test
    fun `the dragon count is still the number of dragons taken, whatever their type`() {
        assertEquals(1, stats.blueTeam.dragons)
        assertEquals(1, stats.redTeam.dragons)

        val json = """{"frames":[{"blueTeam":{"dragons":["ocean","voidborn","infernal"]},"redTeam":{}}]}"""
        val counted = decode(json).toGameStats()!!
        assertEquals(3, counted.blueTeam.dragons)
        assertEquals(0, counted.redTeam.dragons)
        assertEquals(emptyList<DragonType>(), counted.redTeam.dragonTypes)
    }

    @Test
    fun `maps each player's current and maximum health`() {
        assertEquals(
            listOf(PlayerHealth(2651, 2651), PlayerHealth(2174, 2174), PlayerHealth(2149, 2149), PlayerHealth(1456, 1456), PlayerHealth(1788, 1825)),
            stats.blueTeam.players.map { it.health },
        )
        assertEquals(PlayerHealth(2415, 2534), stats.redTeam.players[2].health)
    }

    @Test
    fun `a zero or missing maximum health does not divide by zero`() {
        val json = """
            {"frames":[{"gameState":"in_game","blueTeam":{"participants":[
               {"participantId":1,"currentHealth":120,"maxHealth":0},
               {"participantId":2,"currentHealth":120},
               {"participantId":3,"maxHealth":800}]}}]}
        """
        val players = decode(json).toGameStats()!!.blueTeam.players

        assertEquals(PlayerHealth(120, 0), players[0].health)
        assertEquals(0f, players[0].health!!.fraction, 0f)
        assertNull(players[1].health)
        assertEquals(PlayerHealth(0, 800), players[2].health)
        assertEquals(0f, players[2].health!!.fraction, 0f)
    }

    @Test
    fun `a finished game exposes no health but keeps its dragons`() {
        val json = """
            {"frames":[{"gameState":"finished","blueTeam":{"dragons":["ocean","elder"],"participants":[
               {"participantId":1,"currentHealth":500,"maxHealth":1000}]}}]}
        """
        val finished = decode(json).toGameStats()!!

        assertTrue(finished.isFinished)
        assertNull(finished.blueTeam.players.single().health)
        assertFalse(finished.showsHealth())
        assertEquals(listOf(DragonType.OCEAN, DragonType.ELDER), finished.blueTeam.dragonTypes)
        assertTrue(stats.showsHealth())
    }
}
