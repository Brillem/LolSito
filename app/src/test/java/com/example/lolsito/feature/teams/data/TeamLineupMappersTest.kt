package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamLineupMappersTest {

    private fun decode(json: String) = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(json)

    private val window = decode(Fixtures.read("livestats_window.json"))

    @Test
    fun `the blue team's lineup is its five player ids`() {
        assertEquals(
            setOf("109828407662355333", "109828289393968653", "113628612585554951", "109828431746365782", "112834609700454416"),
            window.lineupOf("107251245690956393"),
        )
    }

    @Test
    fun `the red team's lineup is its own five player ids`() {
        assertEquals(
            setOf("100131733430915231", "108494063372887027", "103478281329357326", "108613483446049530", "99566408211536727"),
            window.lineupOf("100205576309502431"),
        )
    }

    @Test
    fun `a team that did not play gives nothing`() {
        assertNull(window.lineupOf("0000"))
    }

    @Test
    fun `a window without metadata gives nothing`() {
        assertNull(decode("""{"esportsGameId":"1"}""").lineupOf("107251245690956393"))
        assertNull(decode("""{"gameMetadata":{}}""").lineupOf("107251245690956393"))
    }

    @Test
    fun `participants without a player id are left out and no ids at all gives nothing`() {
        val json = """{"gameMetadata":{"blueTeamMetadata":{"esportsTeamId":"t","participantMetadata":[
            {"participantId":1,"esportsPlayerId":"p1"},{"participantId":2},{"participantId":3,"esportsPlayerId":""}]},
            "redTeamMetadata":{"esportsTeamId":"u","participantMetadata":[{"participantId":6}]}}}"""
        assertEquals(setOf("p1"), decode(json).lineupOf("t"))
        assertNull(decode(json).lineupOf("u"))
    }
}
