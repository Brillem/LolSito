package com.example.lolsito.feature.match.ui

import app.cash.turbine.test
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MatchDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMatchDetailRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial load shows loading then content`() = runTest(dispatcher) {
        repository.detail = detail("m1")

        val vm = MatchDetailViewModel(matchId = "m1", repository = repository)
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Content(detail("m1")), awaitItem())
        }
        assertEquals(listOf("m1"), repository.requests)
    }

    @Test
    fun `error then retry shows loading and recovers`() = runTest(dispatcher) {
        repository.error = IOException("boom")

        val vm = MatchDetailViewModel(matchId = "m1", repository = repository)
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Error("boom"), awaitItem())

            repository.error = null
            repository.detail = detail("m1")
            vm.retry()

            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Content(detail("m1")), awaitItem())
        }
        assertEquals(listOf("m1", "m1"), repository.requests)
    }

    @Test
    fun `blank error message falls back to a generic one`() = runTest(dispatcher) {
        repository.error = IOException("")

        val vm = MatchDetailViewModel(matchId = "m1", repository = repository)
        vm.uiState.test {
            assertEquals(MatchDetailUiState.Loading, awaitItem())
            assertEquals(MatchDetailUiState.Error("Something went wrong"), awaitItem())
        }
    }

    private fun detail(id: String) = MatchDetail(
        matchId = id,
        state = MatchState.COMPLETED,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        leagueImageUrl = null,
        blockName = null,
        teams = emptyList(),
        format = SeriesFormat(SeriesType.BEST_OF, 5),
        games = emptyList(),
    )

    private class FakeMatchDetailRepository : MatchDetailRepository {
        var detail: MatchDetail? = null
        var error: Throwable? = null
        val requests = mutableListOf<String>()

        override suspend fun getMatchDetail(matchId: String): MatchDetail {
            requests += matchId
            error?.let { throw it }
            return detail ?: error("No detail")
        }
    }
}
