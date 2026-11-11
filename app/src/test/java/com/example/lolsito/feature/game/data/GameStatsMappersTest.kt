package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
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
            TeamStats(totalGold = 31850, kills = 2, towers = 0, dragons = 1, barons = 0, players = stats.blueTeam.players),
            stats.blueTeam,
        )
        assertEquals(
            TeamStats(totalGold = 33191, kills = 3, towers = 2, dragons = 1, barons = 0, players = stats.redTeam.players),
            stats.redTeam,
        )
        assertFalse(stats.isFinished)
    }

    @Test
    fun `joins each team's players with their metadata by participant id`() {
        assertEquals(
            PlayerStats(1, "MVK Kratos", "Olaf", kills = 0, deaths = 2, assists = 0, creepScore = 142, level = 12),
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
            PlayerStats(9, "FUR Ayu", "Yunara", kills = 1, deaths = 2, assists = 0, creepScore = 155, level = 10),
            stats.redTeam.players[3],
        )
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
}
