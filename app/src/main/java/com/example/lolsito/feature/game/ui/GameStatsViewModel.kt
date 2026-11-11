package com.example.lolsito.feature.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.GameStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [stats] is set only while [status] is [LoadStatus.Content]. */
data class GameStatsUiState(val status: LoadStatus = LoadStatus.Loading, val stats: GameStats? = null)

/** Loads the latest stats of one game once; [retry] loads them again. */
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
