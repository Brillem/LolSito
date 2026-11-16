package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.match.domain.GameOutcome
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.testutil.FakeScheduleRepository
import com.example.lolsito.testutil.FakeTeamsRepository
import com.example.lolsito.testutil.FakeUserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailGameOutcomesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = FakeUserPreferences()
    private val winners = mutableMapOf<String, String?>()
    private val failing = mutableSetOf<String>()
    private val asked = mutableListOf<String>()

    private fun game(number: Int, state: GameState = GameState.COMPLETED) =
        MatchGame(number = number, id = "g$number", state = state, sides = emptyMap())

    private var detail = MatchDetail(
        matchId = "m1",
        state = MatchState.COMPLETED,
        leagueName = "LCK",
        leagueSlug = "lck",
        leagueImageUrl = null,
        blockName = null,
        teams = listOf(
            Team("Gen.G", "GEN", null, null, gameWins = 2, id = "gen"),
            Team("T1", "T1", null, null, gameWins = 1, id = "t1"),
        ),
        format = null,
        games = listOf(game(1), game(2), game(3), game(4, GameState.UNNEEDED), game(5, GameState.UNNEEDED)),
    )

    private val repository = object : MatchDetailRepository {
        override suspend fun getMatchDetail(matchId: String): MatchDetail = detail
    }

    private fun viewModel() = MatchDetailViewModel(
        matchId = "m1",
        repository = repository,
        preferences = preferences,
        teamsRepository = FakeTeamsRepository(),
        scheduleRepository = FakeScheduleRepository(),
        leaguesRepository = { emptyList() },
        zoneId = ZoneOffset.UTC,
        gameWinners = { gameId ->
            asked += gameId
            if (gameId in failing) throw IOException("no network")
            winners[gameId]
        },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each completed game gets its winner and the series score after it`() = runTest(dispatcher) {
        winners += mapOf("g1" to "gen", "g2" to "t1", "g3" to "gen")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            mapOf("g1" to GameOutcome(0, "1 - 0"), "g2" to GameOutcome(1, "1 - 1"), "g3" to GameOutcome(0, "2 - 1")),
            vm.gameOutcomes.value,
        )
        assertEquals(listOf("g1", "g2", "g3"), asked.sorted())
    }

    @Test
    fun `a game the stats cannot tell, or whose request fails, is settled by the series score`() = runTest(dispatcher) {
        winners += mapOf("g1" to "gen", "g2" to null)
        failing += "g3"

        val vm = viewModel()
        advanceUntilIdle()

        // GEN won 2 and T1 1: with game 1 to GEN, games 2 and 3 could go either way.
        assertEquals(mapOf("g1" to GameOutcome(0, "1 - 0")), vm.gameOutcomes.value)

        // Once a second one is known, the last falls into place.
        winners["g2"] = "t1"
        failing.clear()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(GameOutcome(0, "2 - 1"), vm.gameOutcomes.value["g3"])
    }

    @Test
    fun `with no spoilers the winners stay hidden until the result is revealed`() = runTest(dispatcher) {
        preferences.noSpoilers = true
        winners += mapOf("g1" to "gen", "g2" to "t1", "g3" to "gen")

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.gameOutcomes.value.isEmpty())

        vm.revealResult()

        assertEquals(3, vm.gameOutcomes.value.size)
    }

    @Test
    fun `a series without completed games asks for no winner`() = runTest(dispatcher) {
        detail = detail.copy(state = MatchState.UNSTARTED, games = listOf(game(1, GameState.UNSTARTED)))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.gameOutcomes.value.isEmpty())
        assertTrue(asked.isEmpty())
    }
}
