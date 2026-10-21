package com.example.lolsito.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.groupEventsByDay
import com.example.lolsito.feature.schedule.domain.mergeEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

sealed interface ScheduleStatus {
    data object Loading : ScheduleStatus
    data object Content : ScheduleStatus
    data object Empty : ScheduleStatus
    data class Error(val message: String) : ScheduleStatus
}

data class ScheduleUiState(
    val leagues: List<League> = emptyList(),
    val selectedLeagueIds: Set<String> = emptySet(),
    val status: ScheduleStatus = ScheduleStatus.Loading,
    val days: List<ScheduleDay> = emptyList(),
    val canLoadOlder: Boolean = false,
    val canLoadNewer: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isLoadingNewer: Boolean = false,
    val pagingError: String? = null,
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val zoneId: ZoneId,
    defaultLeagueIds: Set<String> = setOf(DEFAULT_LEAGUE_ID),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(selectedLeagueIds = defaultLeagueIds))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var events: List<ScheduleEvent> = emptyList()
    private var olderToken: String? = null
    private var newerToken: String? = null
    private var loadJob: Job? = null
    private var pagingJob: Job? = null

    init {
        reload()
    }

    fun retry() = reload()

    fun toggleLeague(leagueId: String) {
        _uiState.update { state ->
            val selected = state.selectedLeagueIds
            state.copy(selectedLeagueIds = if (leagueId in selected) selected - leagueId else selected + leagueId)
        }
        reload()
    }

    fun loadOlder() = loadPage(older = true)

    fun loadNewer() = loadPage(older = false)

    /** Tokens belong to a filter, so any reload starts pagination from scratch. */
    private fun reload() {
        loadJob?.cancel()
        pagingJob?.cancel()
        events = emptyList()
        olderToken = null
        newerToken = null
        _uiState.update {
            it.copy(
                status = ScheduleStatus.Loading,
                days = emptyList(),
                canLoadOlder = false,
                canLoadNewer = false,
                isLoadingOlder = false,
                isLoadingNewer = false,
                pagingError = null,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                if (_uiState.value.leagues.isEmpty()) {
                    val leagues = leaguesRepository.getLeagues()
                    _uiState.update { it.copy(leagues = leagues) }
                }
                val selected = _uiState.value.selectedLeagueIds
                if (selected.isEmpty()) {
                    publish()
                    return@launch
                }
                val page = repository.getSchedule(selected.sorted(), pageToken = null)
                events = mergeEvents(emptyList(), page.events)
                olderToken = page.olderToken
                newerToken = page.newerToken
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(status = ScheduleStatus.Error(e.toMessage())) }
            }
        }
    }

    private fun loadPage(older: Boolean) {
        val token = (if (older) olderToken else newerToken) ?: return
        if (pagingJob?.isActive == true || loadJob?.isActive == true) return
        val selected = _uiState.value.selectedLeagueIds.sorted()
        _uiState.update {
            it.copy(isLoadingOlder = older, isLoadingNewer = !older, pagingError = null)
        }
        pagingJob = viewModelScope.launch {
            try {
                val page = repository.getSchedule(selected, token)
                events = mergeEvents(events, page.events)
                if (older) olderToken = page.olderToken else newerToken = page.newerToken
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingOlder = false, isLoadingNewer = false, pagingError = e.toMessage())
                }
            }
        }
    }

    private fun publish() {
        _uiState.update {
            it.copy(
                status = if (events.isEmpty()) ScheduleStatus.Empty else ScheduleStatus.Content,
                days = groupEventsByDay(events, zoneId),
                canLoadOlder = olderToken != null,
                canLoadNewer = newerToken != null,
                isLoadingOlder = false,
                isLoadingNewer = false,
            )
        }
    }

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

    companion object {
        const val DEFAULT_LEAGUE_ID = "98767975604431411" // Worlds
    }
}
