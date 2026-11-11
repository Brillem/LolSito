package com.example.lolsito.feature.match.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.polling.MATCH_DETAIL_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState
    data class Error(val message: String) : MatchDetailUiState
    data class Content(val detail: MatchDetail) : MatchDetailUiState
}

/**
 * Loads one match. While its series is in progress, [pollWhileVisible] reloads it every
 * [MATCH_DETAIL_POLL_INTERVAL] and stops by itself once the series is completed.
 */
class MatchDetailViewModel(
    private val matchId: String,
    private val repository: MatchDetailRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /**
     * Keeps the content fresh for as long as it is in progress. The screen runs it only while visible,
     * so leaving the screen cancels it. A refresh that fails leaves the content as it was; an initial
     * load that is still running, failed or completed is never polled, and [retry] pauses polling
     * until its own load ends.
     */
    suspend fun pollWhileVisible() {
        val inProgress = uiState.map { (it as? MatchDetailUiState.Content)?.detail?.state == MatchState.IN_PROGRESS }
        pollWhile(MATCH_DETAIL_POLL_INTERVAL, inProgress) { refresh() }
    }

    private suspend fun refresh() {
        val detail = repository.getMatchDetail(matchId)
        // Only replace content: a reload started meanwhile owns the state.
        _uiState.update { if (it is MatchDetailUiState.Content) MatchDetailUiState.Content(detail) else it }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = MatchDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                MatchDetailUiState.Content(repository.getMatchDetail(matchId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MatchDetailUiState.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong")
            }
        }
    }
}
