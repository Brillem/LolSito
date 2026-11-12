package com.example.lolsito.feature.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.polling.GAME_STATS_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import com.example.lolsito.feature.game.domain.GoldHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [stats] is set only while [status] is [LoadStatus.Content]. */
data class GameStatsUiState(
    val status: LoadStatus = LoadStatus.Loading,
    val stats: GameStats? = null,
    /** Gold difference so far; null while it loads, when it failed, or without [stats]. */
    val goldHistory: GoldHistory? = null,
)

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
     * A refresh that fails, or finds no frames, leaves the stats as they were, and one without details
     * keeps the last details; an initial load that is still running, empty, failed or finished is never
     * polled, and [retry] pauses polling until its own load ends.
     */
    suspend fun pollWhileVisible() {
        val live = uiState.map { it.status == LoadStatus.Content && it.stats?.isFinished == false }
        pollWhile(GAME_STATS_POLL_INTERVAL, live) { refresh() }
    }

    private suspend fun refresh() {
        val stats = repository.getGameStats(gameId) ?: return
        // Only replace content: a reload started meanwhile owns the state.
        // Details are secondary: a refresh that came back without them keeps the last ones.
        // The gold history, once loaded, grows with each refreshed frame.
        _uiState.update {
            if (it.status != LoadStatus.Content) it
            else {
                val next = it.stats?.let(stats::keepingDetailsFrom) ?: stats
                GameStatsUiState(LoadStatus.Content, next, it.goldHistory?.including(next))
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = GameStatsUiState(LoadStatus.Loading)
        loadJob = viewModelScope.launch {
            val state = try {
                val stats = repository.getGameStats(gameId)
                if (stats == null) GameStatsUiState(LoadStatus.Empty) else GameStatsUiState(LoadStatus.Content, stats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GameStatsUiState(LoadStatus.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"))
            }
            _uiState.value = state
            // A child of this load, so a reload cancels it; the stats never wait for it.
            state.stats?.let { launch { loadGoldHistory(it) } }
        }
    }

    /**
     * Backfills the gold history once, up to the frame of [stats]; later refreshes extend it. It is
     * secondary: when it fails or has nothing, the stats stay as they are, without it.
     */
    private suspend fun loadGoldHistory(stats: GameStats) {
        val until = stats.frameTime ?: return
        val history = try {
            repository.getGoldHistory(gameId, until)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        _uiState.update { state ->
            val shown = state.stats
            if (state.status != LoadStatus.Content || shown == null) state
            else state.copy(goldHistory = history.including(shown))
        }
    }
}
