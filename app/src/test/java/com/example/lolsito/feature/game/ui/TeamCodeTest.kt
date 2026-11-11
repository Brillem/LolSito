package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.data.toGameStats
import com.example.lolsito.feature.game.domain.GameTeam
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamCodeTest {

    private val teams = mapOf(
        "t1" to GameTeam("T1", "https://img/t1.png"),
        "100t" to GameTeam("100T", null),
    )

    @Test
    fun `a team id finds its code and logo`() {
        assertEquals(GameTeam("T1", "https://img/t1.png"), teamFor("t1", teams))
        assertEquals(GameTeam("100T", null), teamFor("100t", teams))
    }

    @Test
    fun `an unknown, missing or blank-code id has no team`() {
        assertNull(teamFor("gen", teams))
        assertNull(teamFor(null, teams))
        assertNull(teamFor("t1", emptyMap()))
        assertNull(teamFor("t1", mapOf("t1" to GameTeam(" ", "https://img/t1.png"))))
    }

    @Test
    fun `a blank logo url counts as no logo`() {
        assertNull(teamFor("t1", mapOf("t1" to GameTeam("T1", " ")))!!.logoUrl)
        assertNull(teamFor("t1", mapOf("t1" to GameTeam("T1", "")))!!.logoUrl)
    }

    @Test
    fun `labels follow the sides of the feed, not the ones event details report`() {
        val teams = listOf(
            Team("T1", "T1", "https://img/t1.png", null, null, id = "t1"),
            Team("100 Thieves", "100T", "https://img/100t.png", null, null, id = "100t"),
        )
        // Event details say T1 played blue ...
        val game = MatchGame(1, "g1", GameState.COMPLETED, sides = mapOf("t1" to GameSide.BLUE, "100t" to GameSide.RED))
        val link = game.statsLink(teams)!!
        // ... but the livestats feed has T1 on the red side.
        val json = """
            {"gameMetadata":{
               "blueTeamMetadata":{"esportsTeamId":"100t","participantMetadata":[]},
               "redTeamMetadata":{"esportsTeamId":"t1","participantMetadata":[]}},
             "frames":[{"gameState":"finished"}]}
        """
        val stats = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(json).toGameStats()!!

        val blue = teamFor(stats.blueTeamId, link.teams)
        val red = teamFor(stats.redTeamId, link.teams)
        assertEquals("100T", teamLabel(blue?.code, "Blue side"))
        assertEquals("T1", teamLabel(red?.code, "Red side"))
        assertEquals("https://img/100t.png", blue?.logoUrl)
        assertEquals("https://img/t1.png", red?.logoUrl)
    }

    @Test
    fun `a side whose id matches no team keeps its side name`() {
        assertEquals("Blue side", teamLabel(teamFor("other", teams)?.code, "Blue side"))
        assertEquals("Red side", teamLabel(teamFor(null, teams)?.code, "Red side"))
    }
}
