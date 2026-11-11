package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.data.toGameStats
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamCodeTest {

    private val codes = mapOf("t1" to "T1", "100t" to "100T")

    @Test
    fun `a team id finds its code`() {
        assertEquals("T1", teamCodeFor("t1", codes))
        assertEquals("100T", teamCodeFor("100t", codes))
    }

    @Test
    fun `an unknown, missing or blank id has no code`() {
        assertNull(teamCodeFor("gen", codes))
        assertNull(teamCodeFor(null, codes))
        assertNull(teamCodeFor("t1", emptyMap()))
        assertNull(teamCodeFor("t1", mapOf("t1" to " ")))
    }

    @Test
    fun `labels follow the sides of the feed, not the ones event details report`() {
        val teams = listOf(
            Team("T1", "T1", null, null, null, id = "t1"),
            Team("100 Thieves", "100T", null, null, null, id = "100t"),
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

        assertEquals("100T", teamLabel(teamCodeFor(stats.blueTeamId, link.teamCodes), "Blue side"))
        assertEquals("T1", teamLabel(teamCodeFor(stats.redTeamId, link.teamCodes), "Red side"))
    }

    @Test
    fun `a side whose id matches no team keeps its side name`() {
        assertEquals("Blue side", teamLabel(teamCodeFor("other", codes), "Blue side"))
        assertEquals("Red side", teamLabel(teamCodeFor(null, codes), "Red side"))
    }
}
