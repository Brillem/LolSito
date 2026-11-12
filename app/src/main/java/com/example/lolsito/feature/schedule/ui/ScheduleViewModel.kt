package com.example.lolsito.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.UniqueKeys
import com.example.lolsito.core.polling.LIVE_SECTION_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.LiveMatchesRepository
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.buildScheduleSections
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import com.example.lolsito.feature.schedule.domain.involvesFavorite
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
    /** The "Upcoming" section: unstarted matches by day, soonest first. */
    val upcomingDays: List<ScheduleDay> = emptyList(),
    /** The "Results" section: finished matches by day, most recent first. */
    val resultDays: List<ScheduleDay> = emptyList(),
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
     * The "Live now" section: live matches of the selected leagues, earliest start first; with
     * [liveOnly] the live matches of every league. Empty (section hidden) when nothing is live.
     */
    val liveEvents: List<ScheduleEvent> = emptyList(),
    /** The LIVE toggle: show only the live matches, of every league, whatever the selection. */
    val liveOnly: Boolean = false,
    /** Every match the live feed currently reports, of any league; null until it answered once. */
    val liveMatchIds: Set<String>? = null,
    /** The eye toggle: finished results are hidden (see [eventRowDisplay]). Remembered between launches. */
    val noSpoilers: Boolean = false,
    /** Names of the starred teams (see [favoriteNamesOf]); rows show a star next to a team with one of them. */
    val favoriteNames: List<String> = emptyList(),
    /** The Favorites toggle: only matches of a favorite show. Not remembered, like [liveOnly]. */
    val favoritesOnly: Boolean = false,
) {
    /**
     * What to say when the Favorites toggle leaves nothing to show; null when it is off or something
     * shows. With [liveOnly] on, only the live matches count (the sections are not shown).
     */
    val favoritesEmptyMessage: String?
        get() = when {
            !favoritesOnly || liveEvents.isNotEmpty() -> null
            favoriteNames.isEmpty() -> NO_FAVORITES_MESSAGE
            liveOnly -> "No live matches of your favorites right now"
            upcomingDays.isEmpty() && resultDays.isEmpty() -> "No matches of your favorites in the loaded range"
            else -> null
        }

    /** Only one page loads at a time, so both paging buttons are disabled while one is in flight. */
    val isPaging: Boolean get() = isLoadingOlder || isLoadingNewer

    /** LazyColumn keys aligned with [liveEvents]; the prefix keeps them apart from the day list's keys. */
    val liveKeys: List<String> by lazy {
        val unique = UniqueKeys()
        liveEvents.map { unique.next("live:${it.matchId}") }
    }
}

/**
 * Loads the schedule of the selected leagues, plus a "Live now" section fed by [liveRepository].
 *
 * The screen shows three sections, see [buildScheduleSections]: live matches first, then upcoming
 * ones soonest first, then results most recent first. A live match appears only in the live section
 * and moves to the results once the feed stops reporting it. Paging, merging and the tokens work on
 * the flat chronological event list, which is split into sections whenever it or the feed changes.
 * The live feed never blocks or fails the schedule: if it fails the section is hidden (or keeps its
 * last content when refreshing) and each event's own state decides what is live. The feed covers every
 * league, so the section is the feed filtered by the current selection and needs no request when the
 * selection changes. The LIVE toggle ([toggleLiveOnly]) swaps the page for the feed's matches of all
 * leagues; it leaves the selection and the loaded schedule alone and is not remembered.
 *
 * The no-spoilers mode ([toggleNoSpoilers]) is saved the same way and restored before the first request.
 *
 * The favorite teams are read the same way, before the first request, and again on [refreshFavorites]
 * (they are marked in a team's profile, and this view model survives tab switches). The Favorites
 * toggle ([toggleFavoritesOnly]) keeps only matches of favorites within what is loaded, composes with
 * the LIVE toggle and is not remembered. Unreadable favorites count as none.
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

    /** True once the stored no-spoilers mode was read or superseded by a user toggle. */
    private var noSpoilersRestored = false

    /** True once the stored favorites were read for the first time. */
    private var favoritesRestored = false

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

    /** Turning it on refreshes the live matches at once; the request is skipped if one is already in flight. */
    fun toggleLiveOnly() {
        val on = !_uiState.value.liveOnly
        _uiState.update { it.copy(liveOnly = on).withSections() }
        if (on) loadLive()
    }

    /** Only filters what is loaded: no request, no saving. */
    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly).withSections() }
    }

    /** Reads the favorites again, as the screen comes back; an unreadable preference keeps the current ones. */
    fun refreshFavorites() {
        viewModelScope.launch { readFavorites() }
    }

    private suspend fun restoreFavorites() {
        if (!favoritesRestored) readFavorites()
    }

    private suspend fun readFavorites() {
        val stored = try {
            preferences.getFavoriteTeams()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        favoritesRestored = true
        if (stored == null) return
        val names = favoriteNamesOf(stored)
        _uiState.update { if (it.favoriteNames == names) it else it.copy(favoriteNames = names).withSections() }
    }

    /** Flips the mode at once and remembers it; Standings and Match detail read it when they are shown. */
    fun toggleNoSpoilers() {
        noSpoilersRestored = true
        val on = !_uiState.value.noSpoilers
        _uiState.update { it.copy(noSpoilers = on) }
        viewModelScope.launch {
            try {
                preferences.saveNoSpoilers(on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not remembering the mode is better than failing the toggle.
            }
        }
    }

    /** Runs before the first request, so a stored "on" never flashes results; unreadable counts as off. */
    private suspend fun restoreNoSpoilers() {
        if (noSpoilersRestored) return
        val stored = try {
            preferences.getNoSpoilers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // A toggle made while reading wins over the stored value.
        if (noSpoilersRestored) return
        noSpoilersRestored = true
        if (stored != null) _uiState.update { it.copy(noSpoilers = stored) }
    }

    fun toggleLeague(leagueId: String) {
        _uiState.update { state ->
            val selected = state.selectedLeagueIds
            state.copy(selectedLeagueIds = if (leagueId in selected) selected - leagueId else selected + leagueId)
                .withSections()
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
                canLoadOlder = false,
                canLoadNewer = false,
                isLoadingOlder = false,
                isLoadingNewer = false,
                olderError = null,
                newerError = null,
            ).withSections()
        }
        loadJob = viewModelScope.launch {
            try {
                restoreNoSpoilers()
                restoreFavorites()
                if (_uiState.value.leagues.isEmpty()) {
                    val leagues = leaguesRepository.getLeagues()
                    // One update, so the chips never show the default selection next to the real leagues.
                    val restored = if (selectionRestored) null else readStoredSelection(leagues)
                    _uiState.update {
                        it.copy(leagues = leagues, selectedLeagueIds = restored ?: it.selectedLeagueIds).withSections()
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
                canLoadOlder = olderToken != null,
                canLoadNewer = newerToken != null,
                isLoadingOlder = false,
                isLoadingNewer = false,
            ).withSections()
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
            _uiState.update { it.withSections() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The section is secondary: keep whatever it showed (nothing, the first time).
        } finally {
            liveLoading = false
        }
    }

    /** Recomputes the sections from the loaded events, the last live feed, the selection and the toggle. */
    private fun ScheduleUiState.withSections(): ScheduleUiState {
        val shown: (ScheduleEvent) -> Boolean = { !favoritesOnly || it.involvesFavorite(favoriteNames) }
        val sections = buildScheduleSections(
            events = events.filter(shown),
            liveFeed = lastLiveIds?.let { lastLiveMatches.filter(shown) },
            liveLeagueIds = if (liveOnly) null else selectedLeagueIds,
            zoneId = zoneId,
        )
        return copy(
            liveEvents = sections.live,
            upcomingDays = sections.upcoming,
            resultDays = sections.results,
            liveMatchIds = lastLiveIds,
        )
    }

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

    companion object {
        const val DEFAULT_LEAGUE_ID = "98767975604431411" // Worlds
    }
}

private const val NO_FAVORITES_MESSAGE = "No favorite teams yet. Star a team in its profile."
