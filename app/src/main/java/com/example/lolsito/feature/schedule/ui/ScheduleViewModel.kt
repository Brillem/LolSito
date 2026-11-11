package com.example.lolsito.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.core.polling.LIVE_SECTION_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
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
    /**
     * The "Live now" section: live matches of the selected leagues, earliest start first. Empty
     * (section hidden) when nothing is live, nothing selected, or the live feed has not answered.
     */
    val liveEvents: List<ScheduleEvent> = emptyList(),
    /** Every match the live feed currently reports, of any league; null until it answered once. */
    val liveMatchIds: Set<String>? = null,
) {
    /** Only one page loads at a time, so both paging buttons are disabled while one is in flight. */
    val isPaging: Boolean get() = isLoadingOlder || isLoadingNewer

    /** LazyColumn keys aligned with [liveEvents]; the prefix keeps them apart from the day list's keys. */
    val liveKeys: List<String> by lazy {
        val unique = UniqueKeys()
        liveEvents.map { unique.next("live:${it.matchId}") }
    }

    /**
     * Whether [event] gets the LIVE badge. A match is both in the "Live now" section and, with the
     * badge, at its chronological place in the day list (see [ScheduleViewModel]). Once the live feed
     * answered it decides, so a match that finished loses the badge even if the day list still holds
     * its in-progress copy; until then (or if it never answers) the event's own state decides.
     */
    fun isLive(event: ScheduleEvent): Boolean =
        liveMatchIds?.let { event.matchId in it } ?: (event.state == MatchState.IN_PROGRESS)
}

/**
 * Loads the schedule of the selected leagues, plus a "Live now" section fed by [liveRepository].
 *
 * A live match stays in the day list too, badged: the list is the full chronological schedule and
 * paging, merging and the day headers rely on it being stable, so matches never jump in and out of it
 * as they start or end; the section is a shortcut to them. The live feed never blocks or fails the
 * schedule: if it fails the section is hidden (or keeps its last content when refreshing) and
 * the day list falls back to each event's own state. The feed covers every league, so the section is
 * the feed filtered by the current selection and needs no request when the selection changes.
 *
 * The selection is saved in [preferences] on every
 * toggle and restored once, before the first schedule request, so a stored selection never costs a
 * request for the default one. Preferences failures are ignored: the screen works with the defaults.
 */
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val liveRepository: LiveMatchesRepository,
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

    /** Last live matches the feed returned, of every league. */
    private var lastLiveMatches: List<ScheduleEvent> = emptyList()
    private var lastLiveIds: Set<String>? = null
    private var liveLoading = false

    /** True once the stored selection was applied or superseded (a user toggle, or nothing usable stored). */
    private var selectionRestored = false

    /** The live request does not depend on the selection, so it starts first and runs beside the schedule. */
    init {
        loadLive()
        reload()
    }

    fun retry() {
        loadLive()
        reload()
    }

    /**
     * Refreshes the "Live now" section every [LIVE_SECTION_POLL_INTERVAL]. The screen runs it only
     * while visible, so leaving the screen cancels it. The schedule itself is not reloaded; a failed
     * refresh keeps the last section.
     */
    suspend fun pollWhileVisible() {
        pollWhile(LIVE_SECTION_POLL_INTERVAL) { refreshLive() }
    }

    fun toggleLeague(leagueId: String) {
        _uiState.update { state ->
            val selected = state.selectedLeagueIds
            state.copy(selectedLeagueIds = if (leagueId in selected) selected - leagueId else selected + leagueId)
                .withLiveSection()
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
                    _uiState.update {
                        it.copy(leagues = leagues, selectedLeagueIds = restored ?: it.selectedLeagueIds).withLiveSection()
                    }
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

    private fun loadLive() {
        viewModelScope.launch { refreshLive() }
    }

    /** Skipped while a live request is in flight, so requests never overlap. */
    private suspend fun refreshLive() {
        if (liveLoading) return
        liveLoading = true
        try {
            lastLiveMatches = liveRepository.getLiveMatches()
            lastLiveIds = lastLiveMatches.mapNotNullTo(HashSet()) { it.matchId }
            _uiState.update { it.withLiveSection() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The section is secondary: keep whatever it showed (nothing, the first time).
        } finally {
            liveLoading = false
        }
    }

    private fun ScheduleUiState.withLiveSection() = copy(
        liveEvents = lastLiveMatches.filter { it.leagueId in selectedLeagueIds }.sortedBy { it.startTime },
        liveMatchIds = lastLiveIds,
    )

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

    companion object {
        const val DEFAULT_LEAGUE_ID = "98767975604431411" // Worlds
    }
}
