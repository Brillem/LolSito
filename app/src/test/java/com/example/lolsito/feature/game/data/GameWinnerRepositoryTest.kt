package com.example.lolsito.feature.game.data

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.testutil.FakeLiveStatsApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GameWinnerRepositoryTest {

    private fun window(gameState: String, blueTowers: Int = 9, blueInhibitors: Int = 2): Response<LiveStatsWindowResponse> =
        Response.success(
            LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(
                """
                {"gameMetadata":{"blueTeamMetadata":{"esportsTeamId":"blue-id"},"redTeamMetadata":{"esportsTeamId":"red-id"}},
                 "frames":[{"gameState":"$gameState",
                   "blueTeam":{"towers":$blueTowers,"inhibitors":$blueInhibitors},
                   "redTeam":{"towers":2,"inhibitors":0}}]}
                """.trimIndent(),
            ),
        )

    private val api = FakeLiveStatsApi()
    private val repository = GameStatsRepositoryImpl(api, Clock.fixed(Instant.parse("2026-11-11T16:11:15Z"), ZoneOffset.UTC))

    @Test
    fun `the winner comes from the final frame of the window feed alone`() = runTest {
        api.window = { window("finished") }

        assertEquals("blue-id", repository.getWinnerTeamId("g1"))
        assertEquals(1, api.windowStartingTimes.size)
        assertTrue(api.detailsStartingTimes.isEmpty())
    }

    @Test
    fun `a winner once known is not asked for again`() = runTest {
        api.window = { window("finished") }

        repository.getWinnerTeamId("g1")
        assertEquals("blue-id", repository.getWinnerTeamId("g1"))
        assertEquals(1, api.windowStartingTimes.size)
    }

    @Test
    fun `a game still being played has no winner and is asked again later`() = runTest {
        api.window = { window("in_game") }
        assertNull(repository.getWinnerTeamId("g1"))

        api.window = { window("finished") }
        assertEquals("blue-id", repository.getWinnerTeamId("g1"))
        assertEquals(2, api.windowStartingTimes.size)
    }

    @Test
    fun `stats that do not tell give no winner`() = runTest {
        api.window = { window("finished", blueTowers = 9, blueInhibitors = 0) }

        assertNull(repository.getWinnerTeamId("g1"))
    }

    @Test
    fun `a game without frames has no winner`() = runTest {
        api.window = { Response.success(204, null as LiveStatsWindowResponse?) }

        assertNull(repository.getWinnerTeamId("g1"))
    }

    @Test
    fun `only the most recently used winners are kept`() = runTest {
        val bounded = GameStatsRepositoryImpl(api, Clock.fixed(Instant.parse("2026-11-11T16:11:15Z"), ZoneOffset.UTC), maxCachedGames = 2)
        api.window = { window("finished") }

        bounded.getWinnerTeamId("g1")
        bounded.getWinnerTeamId("g2")
        bounded.getWinnerTeamId("g1") // g1 is now the most recent, so g3 pushes out g2.
        bounded.getWinnerTeamId("g3")
        assertEquals(3, api.windowStartingTimes.size)

        bounded.getWinnerTeamId("g1")
        assertEquals(3, api.windowStartingTimes.size)
        bounded.getWinnerTeamId("g2")
        assertEquals(4, api.windowStartingTimes.size)
    }
}
