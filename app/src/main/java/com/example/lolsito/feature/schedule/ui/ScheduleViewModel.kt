package com.example.lolsito.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
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
import java.time.ZoneOffset

data class ScheduleUiState(
    val leagues: List<League> = emptyList(),
    val selectedLeagueIds: Set<String> = emptySet(),
    val status: LoadStatus = LoadStatus.Loading,
    val days: List<ScheduleDay> = emptyList(),
    val canLoadOlder: Boolean = false,
    val canLoadNewer: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isLoadingNewer: Boolean = false,
    /** Last failure of each paging direction, shown next to its button until that direction is retried. */
    val olderError: String? = null,
    val newerError: String? = null,
    /** Zone used to group days; the screen formats times with it so both always agree. */
    val zoneId: ZoneId = ZoneOffset.UTC,
) {
    /** Only one page loads at a time, so both paging buttons are disabled while one is in flight. */
    val isPaging: Boolean get() = isLoadingOlder || isLoadingNewer
}

/**
 * Loads the schedule of the selected leagues. The selection is saved in [preferences] on every
 * toggle and restored once, before the first schedule request, so a stored selection never costs a
 * request for the default one. Preferences failures are ignored: the screen works with the defaults.
 */
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val zoneId: ZoneId,
    defaultLeagueIds: Set<String> = setOf(DEFAULT_LEAGUE_ID),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState(selectedLeagueIds = defaultLeagueIds, zoneId = zoneId))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var events: List<ScheduleEvent> = emptyList()
    private var olderToken: String? = null
    private var newerToken: String? = null
    private var loadJob: Job? = null
    private var pagingJob: Job? = null

    /** True once the stored selection was applied or superseded (a user toggle, or nothing usable stored). */
    private var selectionRestored = false

    init {
        reload()
    }

    fun retry() = reload()

    fun toggleLeague(leagueId: String) {
        _uiState.update { state ->
            val selected = state.selectedLeagueIds
            state.copy(selectedLeagueIds = if (leagueId in selected) selected - leagueId else selected + leagueId)
        }
        selectionRestored = true
        val selected = _uiState.value.selectedLeagueIds
        viewModelScope.launch {
            try {
                preferences.saveScheduleLeagueIds(selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not remembering the selection is better than failing the toggle.
            }
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
                status = LoadStatus.Loading,
                days = emptyList(),
                canLoadOlder = false,
                canLoadNewer = false,
                isLoadingOlder = false,
                isLoadingNewer = false,
                olderError = null,
                newerError = null,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                if (_uiState.value.leagues.isEmpty()) {
                    val leagues = leaguesRepository.getLeagues()
                    // One update, so the chips never show the default selection next to the real leagues.
                    val restored = if (selectionRestored) null else readStoredSelection(leagues)
                    _uiState.update { it.copy(leagues = leagues, selectedLeagueIds = restored ?: it.selectedLeagueIds) }
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
                _uiState.update { it.copy(status = LoadStatus.Error(e.toMessage())) }
            }
        }
    }

    /**
     * The stored selection restricted to ids that are still in [leagues], or null to keep the current
     * one: nothing stored, unreadable, or none of the stored ids exist any more. A stored empty set
     * (everything deselected) is kept as is. Runs before the first request, in the same job.
     */
    private suspend fun readStoredSelection(leagues: List<League>): Set<String>? {
        val stored = try {
            preferences.getScheduleLeagueIds()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        selectionRestored = true
        if (stored == null) return null
        val known = leagues.mapTo(HashSet()) { it.id }
        val usable = stored.filterTo(HashSet()) { it in known }
        return if (usable.isEmpty() && stored.isNotEmpty()) null else usable
    }

    private fun loadPage(older: Boolean) {
        val token = (if (older) olderToken else newerToken) ?: return
        if (pagingJob?.isActive == true || loadJob?.isActive == true) return
        val selected = _uiState.value.selectedLeagueIds.sorted()
        _uiState.update {
            if (older) it.copy(isLoadingOlder = true, olderError = null)
            else it.copy(isLoadingNewer = true, newerError = null)
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
                val message = e.toMessage()
                _uiState.update {
                    val idle = it.copy(isLoadingOlder = false, isLoadingNewer = false)
                    if (older) idle.copy(olderError = message) else idle.copy(newerError = message)
                }
            }
        }
    }

    /** An empty range that still has a cursor stays Content, so the user can page past it. */
    private fun publish() {
        val canPage = olderToken != null || newerToken != null
        _uiState.update {
            it.copy(
                status = if (events.isEmpty() && !canPage) LoadStatus.Empty else LoadStatus.Content,
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
