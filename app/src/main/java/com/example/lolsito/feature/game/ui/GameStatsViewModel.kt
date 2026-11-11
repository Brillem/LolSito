package com.example.lolsito.feature.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.polling.GAME_STATS_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [stats] is set only while [status] is [LoadStatus.Content]. */
data class GameStatsUiState(val status: LoadStatus = LoadStatus.Loading, val stats: GameStats? = null)

/**
 * Loads the latest stats of one game; [retry] loads them again. While the game is not finished,
 * [pollWhileVisible] reloads them every [GAME_STATS_POLL_INTERVAL] and stops once it is.
 */
class GameStatsViewModel(
    private val gameId: String,
    private val repository: GameStatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameStatsUiState())
    val uiState: StateFlow<GameStatsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /**
     * Keeps live stats fresh. The screen runs it only while visible, so leaving the screen cancels it.
     * A refresh that fails, or finds no frames, leaves the stats as they were; an initial load that is
     * still running, empty, failed or finished is never polled, and [retry] pauses polling until its
     * own load ends.
     */
    suspend fun pollWhileVisible() {
        val live = uiState.map { it.status == LoadStatus.Content && it.stats?.isFinished == false }
        pollWhile(GAME_STATS_POLL_INTERVAL, live) { refresh() }
    }

    private suspend fun refresh() {
        val stats = repository.getGameStats(gameId) ?: return
        // Only replace content: a reload started meanwhile owns the state.
        _uiState.update { if (it.status == LoadStatus.Content) GameStatsUiState(LoadStatus.Content, stats) else it }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = GameStatsUiState(LoadStatus.Loading)
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val stats = repository.getGameStats(gameId)
                if (stats == null) GameStatsUiState(LoadStatus.Empty) else GameStatsUiState(LoadStatus.Content, stats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GameStatsUiState(LoadStatus.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"))
            }
        }
    }
}
