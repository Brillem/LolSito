package com.example.lolsito.feature.teams.domain

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class TeamLineupTest {

    private fun event(id: String?, start: String, state: MatchState) = ScheduleEvent(
        startTime = Instant.parse(start),
        state = state,
        blockName = null,
        leagueName = "LCK",
        leagueSlug = "lck",
        match = id?.let { Match(it, listOf(Team("T1", "T1", null, null, null)), null) },
    )

    private fun game(number: Int, id: String?, state: GameState) = MatchGame(number, id, state, emptyMap())

    private fun detail(vararg games: MatchGame) = MatchDetail(
        matchId = "m", state = MatchState.COMPLETED, leagueName = "LCK", leagueSlug = null, leagueImageUrl = null,
        blockName = null, teams = emptyList(), format = null, games = games.toList(),
    )

    private fun player(id: String?, name: String, role: PlayerRole) = Player(id, name, null, null, null, role)

    // Candidate matches

    @Test
    fun `candidates are the finished matches, newest first, at most three`() {
        val events = listOf(
            event("m1", "2026-10-01T10:00:00Z", MatchState.COMPLETED),
            event("m4", "2026-10-04T10:00:00Z", MatchState.COMPLETED),
            event("m2", "2026-10-02T10:00:00Z", MatchState.COMPLETED),
            event("m3", "2026-10-03T10:00:00Z", MatchState.COMPLETED),
        )
        assertEquals(listOf("m4", "m3", "m2"), lineupCandidateMatchIds(events))
    }

    @Test
    fun `unfinished matches and matches without an id are skipped`() {
        val events = listOf(
            event("live", "2026-11-12T10:00:00Z", MatchState.IN_PROGRESS),
            event("next", "2026-11-20T10:00:00Z", MatchState.UNSTARTED),
            event(null, "2026-10-09T10:00:00Z", MatchState.COMPLETED),
            event("done", "2026-10-01T10:00:00Z", MatchState.COMPLETED),
        )
        assertEquals(listOf("done"), lineupCandidateMatchIds(events))
        assertTrue(lineupCandidateMatchIds(emptyList()).isEmpty())
    }

    @Test
    fun `a match listed twice is a candidate once`() {
        val events = listOf(
            event("m1", "2026-10-01T10:00:00Z", MatchState.COMPLETED),
            event("m1", "2026-10-01T10:00:00Z", MatchState.COMPLETED),
        )
        assertEquals(listOf("m1"), lineupCandidateMatchIds(events))
    }

    // Last game of a match

    @Test
    fun `the last completed game with an id is the one whose lineup is read`() {
        val match = detail(
            game(1, "g1", GameState.COMPLETED),
            game(2, "g2", GameState.COMPLETED),
            game(3, "g3", GameState.UNNEEDED),
            game(4, "g4", GameState.UNSTARTED),
        )
        assertEquals("g2", match.lastPlayedGameId())
    }

    @Test
    fun `a match without a completed game with an id has no game to read`() {
        assertNull(detail(game(1, "g1", GameState.UNSTARTED), game(2, "g2", GameState.IN_PROGRESS)).lastPlayedGameId())
        assertNull(detail(game(1, null, GameState.COMPLETED)).lastPlayedGameId())
        assertEquals("g1", detail(game(1, "g1", GameState.COMPLETED), game(2, null, GameState.COMPLETED)).lastPlayedGameId())
        assertNull(detail().lastPlayedGameId())
    }

    // Fallback across matches

    @Test
    fun `the first match that gives a lineup wins and the next ones are not asked`() = runTest {
        val asked = mutableListOf<String>()
        val lineup = findLineup(listOf("m1", "m2", "m3")) { id ->
            asked += id
            if (id == "m1") null else setOf("a")
        }
        assertEquals(setOf("a"), lineup)
        assertEquals(listOf("m1", "m2"), asked)
    }

    @Test
    fun `a failing match falls back to the next one`() = runTest {
        val lineup = findLineup(listOf("m1", "m2")) { id -> if (id == "m1") throw IOException("down") else setOf("a") }
        assertEquals(setOf("a"), lineup)
    }

    @Test
    fun `an empty lineup counts as none and it gives up after the candidates`() = runTest {
        val asked = mutableListOf<String>()
        val lineup = findLineup(listOf("m1", "m2", "m3")) { id -> asked += id; emptySet() }
        assertNull(lineup)
        assertEquals(listOf("m1", "m2", "m3"), asked)
        assertNull(findLineup(emptyList()) { setOf("a") })
    }

    // Roster split

    private val top = player("1", "Top", PlayerRole.TOP)
    private val jungle = player("2", "Jungle", PlayerRole.JUNGLE)
    private val mid = player("3", "Mid", PlayerRole.MID)
    private val bottom = player("4", "Bottom", PlayerRole.BOTTOM)
    private val support = player("5", "Support", PlayerRole.SUPPORT)
    private val subTop = player("6", "SubTop", PlayerRole.TOP)
    private val subMid = player("7", "SubMid", PlayerRole.MID)
    private val noId = player(null, "NoId", PlayerRole.SUPPORT)

    private val roster = listOf(
        RoleGroup(PlayerRole.TOP, listOf(subTop, top)),
        RoleGroup(PlayerRole.JUNGLE, listOf(jungle)),
        RoleGroup(PlayerRole.MID, listOf(subMid, mid)),
        RoleGroup(PlayerRole.BOTTOM, listOf(bottom)),
        RoleGroup(PlayerRole.SUPPORT, listOf(support, noId)),
    )

    @Test
    fun `starters come in role order and the rest keeps its groups`() {
        val split = splitRoster(roster, setOf("5", "3", "1", "4", "2"))!!
        assertEquals(listOf(top, jungle, mid, bottom, support), split.starters)
        assertEquals(
            listOf(
                RoleGroup(PlayerRole.TOP, listOf(subTop)),
                RoleGroup(PlayerRole.MID, listOf(subMid)),
                RoleGroup(PlayerRole.SUPPORT, listOf(noId)),
            ),
            split.rest,
        )
    }

    @Test
    fun `a lineup player missing from the roster is ignored`() {
        val split = splitRoster(roster, setOf("1", "2", "3", "4", "ghost"))!!
        assertEquals(listOf(top, jungle, mid, bottom), split.starters)
        assertEquals(listOf(support, noId), split.rest.single { it.role == PlayerRole.SUPPORT }.players)
    }

    @Test
    fun `fewer than five matches still show the ones that match`() {
        val split = splitRoster(roster, setOf("7"))!!
        assertEquals(listOf(subMid), split.starters)
        assertEquals(listOf(PlayerRole.TOP, PlayerRole.JUNGLE, PlayerRole.MID, PlayerRole.BOTTOM, PlayerRole.SUPPORT), split.rest.map { it.role })
        assertEquals(listOf(mid), split.rest.single { it.role == PlayerRole.MID }.players)
    }

    @Test
    fun `no roster player in the lineup means no split`() {
        assertNull(splitRoster(roster, setOf("ghost")))
        assertNull(splitRoster(roster, emptySet()))
        assertNull(splitRoster(emptyList(), setOf("1")))
    }

    @Test
    fun `all of a role starting leaves the role out of the rest`() {
        val split = splitRoster(listOf(RoleGroup(PlayerRole.JUNGLE, listOf(jungle)), RoleGroup(PlayerRole.TOP, listOf(top))), setOf("1", "2"))!!
        assertEquals(listOf(jungle, top), split.starters)
        assertTrue(split.rest.isEmpty())
    }
}
