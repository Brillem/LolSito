package com.example.lolsito.navigation

import androidx.lifecycle.Lifecycle
import com.example.lolsito.feature.game.domain.GameTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchNavigationTest {

    @Test
    fun `navigation starts only from a resumed entry`() {
        assertTrue(canNavigateFrom(Lifecycle.State.RESUMED))
    }

    @Test
    fun `an entry that is already leaving does not navigate again`() {
        assertFalse(canNavigateFrom(Lifecycle.State.STARTED))
        assertFalse(canNavigateFrom(Lifecycle.State.CREATED))
        assertFalse(canNavigateFrom(Lifecycle.State.DESTROYED))
    }

    @Test
    fun `game route keeps the game id path and takes optional team arguments`() {
        assertEquals(
            "game/{gameId}?team1Id={team1Id}&team1Code={team1Code}&team1Logo={team1Logo}&" +
                "team2Id={team2Id}&team2Code={team2Code}&team2Logo={team2Logo}",
            Routes.GAME,
        )
        assertEquals("gameId", Routes.GAME_ID_ARG)
        assertEquals(
            listOf("team1Id", "team1Code", "team1Logo", "team2Id", "team2Code", "team2Logo"),
            Routes.TEAM_ARGS,
        )
    }

    private val fakeEncode: (String) -> String = { "<$it>" }

    @Test
    fun `game route encodes the id and both teams with their logos`() {
        assertEquals(
            "game/<g1>?team1Id=<107>&team1Code=<T1>&team1Logo=<https://img/t1.png>&" +
                "team2Id=<108>&team2Code=<100T>&team2Logo=<https://img/100t.png>",
            gameRoute(
                "g1",
                linkedMapOf("107" to GameTeam("T1", "https://img/t1.png"), "108" to GameTeam("100T", "https://img/100t.png")),
                fakeEncode,
            ),
        )
    }

    @Test
    fun `game route leaves out teams and logos that are not known`() {
        assertEquals("game/<g1>", gameRoute("g1", emptyMap(), fakeEncode))
        assertEquals(
            "game/<g1>?team1Id=<107>&team1Code=<T1>",
            gameRoute("g1", mapOf("107" to GameTeam("T1", null)), fakeEncode),
        )
        assertEquals(
            "game/<g1>?team1Id=<107>&team1Code=<T1>&team2Id=<108>&team2Code=<B>&team2Logo=<l>",
            gameRoute("g1", linkedMapOf("107" to GameTeam("T1", " "), "108" to GameTeam("B", "l")), fakeEncode),
        )
    }

    @Test
    fun `game route keeps at most two teams`() {
        val route = gameRoute(
            "g1",
            linkedMapOf("1" to GameTeam("A"), "2" to GameTeam("B"), "3" to GameTeam("C")),
            fakeEncode,
        )
        assertEquals("game/<g1>?team1Id=<1>&team1Code=<A>&team2Id=<2>&team2Code=<B>", route)
    }

    @Test
    fun `game route urls with reserved characters survive the real encoding`() {
        val url = "https://static.lolesports.com/teams/a b&c=d.png"
        val route = gameRoute("g1", mapOf("1" to GameTeam("A", url)), java.net.URLEncoder::encode)
        assertEquals(false, route.substringAfter("team1Logo=").let { '&' in it || ' ' in it })
        assertEquals(url, java.net.URLDecoder.decode(route.substringAfter("team1Logo="), "UTF-8"))
    }

    @Test
    fun `teams are read back from the route arguments`() {
        val args = mapOf(
            "team1Id" to "107", "team1Code" to "T1", "team1Logo" to "https://img/t1.png",
            "team2Id" to "108", "team2Code" to "100T", "team2Logo" to "https://img/100t.png",
        )
        assertEquals(
            mapOf("107" to GameTeam("T1", "https://img/t1.png"), "108" to GameTeam("100T", "https://img/100t.png")),
            gameTeams(args::get),
        )
    }

    @Test
    fun `a team without a logo is read back without one`() {
        val args = mapOf("team1Id" to "107", "team1Code" to "T1")
        assertEquals(mapOf("107" to GameTeam("T1", null)), gameTeams(args::get))
    }

    @Test
    fun `an incomplete team argument triple is ignored`() {
        val args = mapOf(
            "team1Id" to "107", "team1Logo" to "https://img/t1.png",
            "team2Id" to "108", "team2Code" to "100T",
        )
        assertEquals(mapOf("108" to GameTeam("100T", null)), gameTeams(args::get))
        assertEquals(emptyMap<String, GameTeam>(), gameTeams { null })
        assertEquals(emptyMap<String, GameTeam>(), gameTeams(mapOf("team1Code" to "T1", "team1Logo" to "l")::get))
    }
}
