package com.example.lolsito.feature.teams.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.error.toUserMessage
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.reminders.domain.NotificationAvailability
import com.example.lolsito.feature.reminders.domain.ReminderScheduler
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.teams.domain.MIN_SEARCH_LENGTH
import com.example.lolsito.feature.teams.domain.TeamDirectoryRepository
import com.example.lolsito.feature.teams.domain.TeamSearchIndex
import com.example.lolsito.feature.teams.domain.TeamSearchResults
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.feature.teams.domain.currentTeams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class TeamsUiState(
    val leagues: List<League> = emptyList(),
    val leaguesStatus: LoadStatus = LoadStatus.Loading,
    val selectedLeagueId: String,
    /** The teams of [selectedLeagueId]. */
    val status: LoadStatus = LoadStatus.Loading,
    val teams: List<TeamSummary> = emptyList(),
    /** The teams the user starred, listed above the league picker whatever league is selected. */
    val favorites: List<FavoriteTeam> = emptyList(),
    /** The bell next to the favorites: start-of-match reminders for them. Off until the user turns it on. */
    val remindersEnabled: Boolean = false,
    /**
     * Shown next to the bell: why it could not be turned on (until it is tried again), or that it is on
     * but the system does not allow notifications; null when there is nothing to say.
     */
    val remindersNotice: String? = null,
    /** What the user typed in the search field; blank means the tab shows its usual content. */
    val query: String = "",
    /** The team list the search runs over: Loading while it is fetched, Error when that failed, Content otherwise. */
    val searchStatus: LoadStatus = LoadStatus.Content,
    val searchResults: TeamSearchResults = TeamSearchResults(),
) {
    /** True while the results replace the favorites, the picker and the grid. */
    val isSearching: Boolean get() = query.isNotBlank()

    /** True when the query is too short to search anything yet. */
    val queryTooShort: Boolean get() = query.trim().length < MIN_SEARCH_LENGTH

    /** The picker's label; "League" until the leagues are known. */
    val selectedLeagueName: String get() = leagues.firstOrNull { it.id == selectedLeagueId }?.name ?: "League"
}

/**
 * The Teams tab: the user picks a league and sees its current teams, those of the league's ongoing
 * tournament standings (see [currentTeams]). The leagues only feed the picker, so failing to load them
 * never blocks the teams of the selected league.
 *
 * The favorites (marked in a team's profile) come from [preferences]; they need no request, are read
 * again by [refreshFavorites] whenever the tab comes back, and unreadable ones count as none.
 *
 * The bell next to the favorites turns the start-of-match reminders on and off for all of them. It is
 * off until the user turns it on and is remembered. Turning it on asks [reminderSync] for a sync;
 * turning it off cancels every reminder through [reminderScheduler] at once. The screen asks for the
 * notification permission first when the system needs it ([onNotificationPermissionResult]). While the
 * bell is on and [notifications] says the system would not show them, a notice says so
 * ([refreshNotificationsNotice]); the bell is never turned off for the user.
 *
 * The search field ([onQueryChange]) looks for teams and players among every team of [teamDirectory]. That
 * list is requested the first time the user types, not before, and kept; a failed request shows an error
 * that [retrySearch] requests again. A blank query leaves the tab as it was.
 *
 * The search runs on [searchDispatcher], over the list's [TeamSearchIndex], once typing pauses for
 * [SEARCH_DEBOUNCE_MILLIS]; a newer query cancels a search still running. A blank query clears the results at once.
 */
class TeamsViewModel(
    private val standingsRepository: StandingsRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val reminderScheduler: ReminderScheduler,
    private val reminderSync: ReminderSyncTrigger,
    private val notifications: NotificationAvailability,
    private val teamDirectory: TeamDirectoryRepository,
    defaultLeagueId: String = DEFAULT_LEAGUE_ID,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamsUiState(selectedLeagueId = defaultLeagueId))
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    private var leaguesJob: Job? = null
    private var remindersLoadJob: Job? = null
    private var teamsJob: Job? = null
    private var directoryJob: Job? = null
    private val searchIndex = MutableStateFlow<TeamSearchIndex?>(null)

    /**
     * Every query typed, in order. Not a StateFlow: "t1", "", "t1" typed in a row must search "t1" again after
     * the blank one cleared the results, and a StateFlow collector could see only the two equal "t1".
     */
    private val queries = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .apply { tryEmit("") }

    init {
        observeSearch()
        loadRemindersEnabled()
        refreshFavorites()
        loadLeagues()
        loadTeams()
    }

    fun selectLeague(leagueId: String) {
        if (leagueId == _uiState.value.selectedLeagueId) return
        _uiState.update { it.copy(selectedLeagueId = leagueId) }
        loadTeams()
    }

    /** Sets the search text. The first non-blank one asks for the team list; later ones search what is kept. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queries.tryEmit(query)
        if (query.isBlank()) {
            // Leaving a failed search behind: the next one starts clean. The results go at once, without waiting.
            _uiState.update {
                it.copy(
                    searchResults = TeamSearchResults(),
                    searchStatus = if (it.searchStatus is LoadStatus.Error) LoadStatus.Content else it.searchStatus,
                )
            }
        } else if (searchIndex.value == null && directoryJob?.isActive != true && _uiState.value.searchStatus !is LoadStatus.Error) {
            loadDirectory()
        }
    }

    /** Clears the search text, which brings the usual tab back. */
    fun clearQuery() = onQueryChange("")

    /** Asks again for the team list after it failed. */
    fun retrySearch() = loadDirectory()

    private fun loadDirectory() {
        if (directoryJob?.isActive == true) return
        _uiState.update { it.copy(searchStatus = LoadStatus.Loading) }
        directoryJob = viewModelScope.launch {
            try {
                searchIndex.value = teamDirectory.getSearchIndex()
                _uiState.update { it.copy(searchStatus = LoadStatus.Content) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(searchStatus = LoadStatus.Error(e.toUserMessage())) }
            }
        }
    }

    /**
     * Searches the kept team list for the query once typing pauses, and again when the list arrives or the
     * league order (which decides the team of a duplicated player) changes; nothing to show until the list is
     * here. Results are only shown while the query they were searched for is still the one typed.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearch() {
        val query = queries
            .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        val leagueOrder = _uiState.map { state -> state.leagues.map { it.name } }.distinctUntilChanged()
        viewModelScope.launch {
            combine(query, searchIndex, leagueOrder) { q, index, order -> Triple(q, index, order) }
                .mapLatest { (q, index, order) -> q to (index?.search(q, leagueOrder = order) ?: TeamSearchResults()) }
                .flowOn(searchDispatcher)
                .collect { (q, results) -> _uiState.update { if (it.query == q) it.copy(searchResults = results) else it } }
        }
    }

    /** An unreadable preference keeps the favorites already shown (none at first). */
    fun refreshFavorites() {
        viewModelScope.launch {
            try {
                preferences.getFavoriteTeams()?.let { stored -> _uiState.update { it.copy(favorites = stored) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing the current favorites.
            }
        }
    }

    /**
     * Turns the bell on (the notification permission is already granted or not needed): it flips at
     * once, is remembered, and a sync is asked for so the reminders get scheduled.
     */
    fun enableReminders() {
        remindersLoadJob?.cancel() // The user's choice wins over the stored one still loading.
        _uiState.update { it.copy(remindersEnabled = true, remindersNotice = null) }
        refreshNotificationsNotice()
        viewModelScope.launch {
            saveRemindersEnabled(true)
            reminderSync.requestSync()
        }
    }

    /** Turns the bell off: every reminder is cancelled at once, without waiting for a sync or a network. */
    fun disableReminders() {
        remindersLoadJob?.cancel()
        _uiState.update { it.copy(remindersEnabled = false, remindersNotice = null) }
        reminderScheduler.cancelAll()
        viewModelScope.launch { saveRemindersEnabled(false) }
    }

    /** The answer to the notification permission asked when turning the bell on; denied leaves it off and says why. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            enableReminders()
        } else {
            _uiState.update { it.copy(remindersEnabled = false, remindersNotice = PERMISSION_DENIED_NOTICE) }
        }
    }

    /**
     * Says next to the bell that the system does not allow notifications while the bell is on, and stops
     * saying it once it does or the bell is off. The bell itself is left as the user set it. The screen
     * calls this whenever it is shown again, since the setting changes outside the app.
     */
    fun refreshNotificationsNotice() {
        val blocked = !notifications.canNotify()
        _uiState.update { state ->
            when {
                state.remindersEnabled && blocked -> state.copy(remindersNotice = NOTIFICATIONS_OFF_NOTICE)
                state.remindersNotice == NOTIFICATIONS_OFF_NOTICE -> state.copy(remindersNotice = null)
                else -> state
            }
        }
    }

    fun retry() = loadTeams()

    fun retryLeagues() = loadLeagues()

    private fun loadLeagues() {
        if (leaguesJob?.isActive == true) return
        _uiState.update { it.copy(leaguesStatus = LoadStatus.Loading) }
        leaguesJob = viewModelScope.launch {
            try {
                val leagues = leaguesRepository.getLeagues()
                _uiState.update {
                    it.copy(leagues = leagues, leaguesStatus = if (leagues.isEmpty()) LoadStatus.Empty else LoadStatus.Content)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(leaguesStatus = LoadStatus.Error(e.toUserMessage())) }
            }
        }
    }

    /** A new pick cancels the previous load, so an old league's teams never replace the new ones. */
    private fun loadTeams() {
        teamsJob?.cancel()
        val leagueId = _uiState.value.selectedLeagueId
        _uiState.update { it.copy(status = LoadStatus.Loading, teams = emptyList()) }
        teamsJob = viewModelScope.launch {
            try {
                val teams = standingsRepository.currentTeams(leagueId, LocalDate.now(clock))
                _uiState.update {
                    it.copy(status = if (teams.isEmpty()) LoadStatus.Empty else LoadStatus.Content, teams = teams)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(status = LoadStatus.Error(e.toUserMessage())) }
            }
        }
    }

    /** An unreadable preference counts as off, the default. */
    private fun loadRemindersEnabled() {
        remindersLoadJob = viewModelScope.launch {
            val enabled = try {
                preferences.getMatchRemindersEnabled() ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.update { it.copy(remindersEnabled = enabled) }
            refreshNotificationsNotice()
        }
    }

    /** Not remembering the bell is better than failing the toggle. */
    private suspend fun saveRemindersEnabled(enabled: Boolean) {
        try {
            preferences.saveMatchRemindersEnabled(enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignored, like the favorites star.
        }
    }

    companion object {
        const val DEFAULT_LEAGUE_ID = "98767991310872058" // LCK

        /** How long typing must pause before the query is searched. */
        const val SEARCH_DEBOUNCE_MILLIS = 150L

        const val PERMISSION_DENIED_NOTICE =
            "Reminders need permission to send notifications. Allow notifications for LolSito in the system settings."

        const val NOTIFICATIONS_OFF_NOTICE =
            "Notifications for LolSito are turned off in the system settings, so reminders will not be shown."
    }
}
