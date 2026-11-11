package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsDetailsResponse
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailsMappersTest {

    private fun decode(json: String) = LolEsportsJson.decodeFromString<LiveStatsDetailsResponse>(json)

    private val details = decode(Fixtures.read("livestats_details.json")).toPlayerDetails()!!

    @Test
    fun `maps every participant of the last frame by participant id`() {
        assertEquals((1..10).toList(), details.keys.sorted())
        assertEquals(
            PlayerDetails(
                itemIds = listOf(1055, 1083, 3047, 3363, 3073, 1028, 2055),
                primaryStyleId = 8000,
                secondaryStyleId = 8300,
                keystoneId = 8010,
                damageShare = 0.29073191636216766,
                killParticipation = 0.0,
                totalGold = 5949,
                wardsPlaced = 8,
                wardsDestroyed = 0,
            ),
            details.getValue(1),
        )
        assertEquals(listOf(1086, 3032, 3006, 3363, 3086, 1042, 1042, 2055), details.getValue(9).itemIds)
        assertEquals(8200, details.getValue(10).primaryStyleId)
        assertEquals(8214, details.getValue(10).keystoneId)
    }

    @Test
    fun `no frames means no details`() {
        assertNull(decode("""{"frames":[]}""").toPlayerDetails())
        assertNull(decode("""{}""").toPlayerDetails())
    }

    @Test
    fun `uses the last frame`() {
        val json = """
            {"frames":[
              {"participants":[{"participantId":1,"totalGoldEarned":100}]},
              {"participants":[{"participantId":1,"totalGoldEarned":250}]}]}
        """
        assertEquals(250, decode(json).toPlayerDetails()!!.getValue(1).totalGold)
    }

    @Test
    fun `empty item slots are skipped`() {
        val json = """{"frames":[{"participants":[{"participantId":1,"items":[3047,0,1055,0,0]}]}]}"""

        assertEquals(listOf(3047, 1055), decode(json).toPlayerDetails()!!.getValue(1).itemIds)
    }

    @Test
    fun `missing fields default to nothing, zero and no runes`() {
        val player = decode("""{"frames":[{"participants":[{"participantId":4}]}]}""").toPlayerDetails()!!.getValue(4)

        assertEquals(
            PlayerDetails(
                itemIds = emptyList(),
                primaryStyleId = null,
                secondaryStyleId = null,
                keystoneId = null,
                damageShare = 0.0,
                killParticipation = 0.0,
                totalGold = 0,
                wardsPlaced = 0,
                wardsDestroyed = 0,
            ),
            player,
        )
    }

    @Test
    fun `participants without an id are skipped`() {
        val json = """{"frames":[{"participants":[{"totalGoldEarned":9},{"participantId":2,"totalGoldEarned":5}]}]}"""

        assertEquals(setOf(2), decode(json).toPlayerDetails()!!.keys)
    }

    @Test
    fun `the first perk is the keystone and an empty perk list has none`() {
        val json = """
            {"frames":[{"participants":[
              {"participantId":1,"perkMetadata":{"styleId":8100,"subStyleId":8000,"perks":[8112,8139]}},
              {"participantId":2,"perkMetadata":{"styleId":8100,"perks":[]}}]}]}
        """
        val result = decode(json).toPlayerDetails()!!

        assertEquals(8112, result.getValue(1).keystoneId)
        assertEquals(8000, result.getValue(1).secondaryStyleId)
        assertNull(result.getValue(2).keystoneId)
        assertNull(result.getValue(2).secondaryStyleId)
    }

    @Test
    fun `withDetails attaches details to the players of both teams by participant id`() {
        val stats = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(Fixtures.read("livestats_window.json"))
            .toGameStats()!!

        val joined = stats.withDetails(details)

        assertEquals(details.getValue(1), joined.blueTeam.players.first().details)
        assertEquals(details.getValue(9), joined.redTeam.players.first { it.participantId == 9 }.details)
        assertTrue((joined.blueTeam.players + joined.redTeam.players).all { it.details != null })
        assertEquals(stats.blueTeam.totalGold, joined.blueTeam.totalGold)
    }

    @Test
    fun `withDetails leaves players without a matching participant without details`() {
        val stats = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(Fixtures.read("livestats_window.json"))
            .toGameStats()!!

        val joined = stats.withDetails(mapOf(1 to details.getValue(1)))

        assertEquals(details.getValue(1), joined.blueTeam.players[0].details)
        assertNull(joined.blueTeam.players[1].details)
        assertTrue(joined.redTeam.players.all { it.details == null })
    }

    @Test
    fun `window patch version maps to the Data Dragon version`() {
        val stats = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(Fixtures.read("livestats_window.json"))
            .toGameStats()!!

        assertEquals("16.20.1", stats.dataDragonVersion)
    }

    @Test
    fun `a window without patch version has no Data Dragon version`() {
        val json = """{"frames":[{"blueTeam":{},"redTeam":{}}]}"""

        assertNull(LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(json).toGameStats()!!.dataDragonVersion)
    }
}
