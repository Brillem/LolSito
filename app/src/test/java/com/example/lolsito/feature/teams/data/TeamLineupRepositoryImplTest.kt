package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.network.LiveStatsApi
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LiveStatsDetailsResponse
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class TeamLineupRepositoryImplTest {

    private val blue = "107251245690956393"
    private val window = LolEsportsJson.decodeFromString<LiveStatsWindowResponse>(Fixtures.read("livestats_window.json"))

    private var detail = detailOf(MatchGame(1, "g1", GameState.COMPLETED, emptyMap()), MatchGame(2, "g2", GameState.COMPLETED, emptyMap()))
    private var windowAnswer: Response<LiveStatsWindowResponse> = Response.success(window)
    private val windowRequests = mutableListOf<String>()

    private val matches = object : MatchDetailRepository {
        override suspend fun getMatchDetail(matchId: String) = detail
    }
    private val api = object : LiveStatsApi {
        override suspend fun getWindow(gameId: String, startingTime: String): Response<LiveStatsWindowResponse> {
            windowRequests += gameId
            return windowAnswer
        }

        override suspend fun getDetails(gameId: String, startingTime: String): Response<LiveStatsDetailsResponse> =
            throw AssertionError("details are not needed")
    }
    private val repository = TeamLineupRepositoryImpl(matches, api)

    private fun detailOf(vararg games: MatchGame) = MatchDetail(
        matchId = "m", state = MatchState.COMPLETED, leagueName = "LCK", leagueSlug = null, leagueImageUrl = null,
        blockName = null, teams = emptyList(), format = null, games = games.toList(),
    )

    @Test
    fun `reads the lineup of the team from the window of the last completed game`() = runTest {
        assertEquals(5, repository.getLineup("m", blue)?.size)
        assertEquals(listOf("g2"), windowRequests)
    }

    @Test
    fun `a team that is not in the game gives nothing`() = runTest {
        assertNull(repository.getLineup("m", "other"))
    }

    @Test
    fun `a match without a played game asks the feed nothing`() = runTest {
        detail = detailOf(MatchGame(1, "g1", GameState.UNSTARTED, emptyMap()))
        assertNull(repository.getLineup("m", blue))
        assertEquals(emptyList<String>(), windowRequests)
    }

    @Test
    fun `a 204 gives nothing`() = runTest {
        windowAnswer = Response.success(null)
        assertNull(repository.getLineup("m", blue))
    }

    @Test
    fun `an error answer gives nothing`() = runTest {
        windowAnswer = Response.error(404, "".toResponseBody("text/plain".toMediaType()))
        assertNull(repository.getLineup("m", blue))
    }
}
