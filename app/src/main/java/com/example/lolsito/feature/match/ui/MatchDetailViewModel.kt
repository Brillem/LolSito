package com.example.lolsito.feature.match.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState
    data class Error(val message: String) : MatchDetailUiState
    data class Content(val detail: MatchDetail) : MatchDetailUiState
}

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
