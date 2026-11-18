package com.example.lolsito.feature.standings.ui

import com.example.lolsito.core.error.toUserMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.StandingsSelection
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.favoriteNamesOf
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.groupEventsByDay
import com.example.lolsito.feature.standings.domain.StageTab
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.TeamOption
import com.example.lolsito.feature.standings.domain.defaultStageIndex
import com.example.lolsito.feature.standings.domain.involvesTeam
import com.example.lolsito.feature.standings.domain.isRanked
import com.example.lolsito.feature.standings.domain.loadEventsBetween
import com.example.lolsito.feature.standings.domain.regularStageMatches
import com.example.lolsito.feature.standings.domain.stageRankingOf
import com.example.lolsito.feature.standings.domain.stageStandingLine
import com.example.lolsito.feature.standings.domain.stageTabs
import com.example.lolsito.feature.standings.domain.stageTeamOptions
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.pickDefaultTournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class StandingsUiState(
    val leagues: List<League> = emptyList(),
    val leaguesStatus: LoadStatus = LoadStatus.Loading,
    val selectedLeagueId: String,
    val tournamentsStatus: LoadStatus = LoadStatus.Loading,
    val tournaments: List<Tournament> = emptyList(),
    val selectedTournamentId: String? = null,
    val standingsStatus: LoadStatus = LoadStatus.Loading,
    val rows: List<StandingsRow> = emptyList(),
    /** No-spoilers mode, set in Schedule: finished matches show no score or winner. The ranking tables are not affected. */
    val noSpoilers: Boolean = false,
    /** Names of the starred teams (see [favoriteNamesOf]): their table rows and match cards show a star. Display only. */
    val favoriteNames: List<String> = emptyList(),
    /** A pull to refresh is reloading the standings; [rows] stay as they are meanwhile. */
    val isRefreshing: Boolean = false,
    /** One tab per stage of the loaded standings; the selector shows only with more than one. */
    val stageTabs: List<StageTab> = emptyList(),
    /** Key of the stage [rows] show, one of [stageTabs]. */
    val selectedStageKey: String? = null,
    /** Table or Matches; Matches only applies while [canShowMatches]. */
    val stageView: StageView = StageView.TABLE,
    /** The selected stage is ranked and the tournament has dates, so its matches can be listed. */
    val canShowMatches: Boolean = false,
    val matchesStatus: LoadStatus = LoadStatus.Loading,
    /** The regular stage matches by day, oldest first, only those of [teamFilter] when set. */
    val matchDays: List<ScheduleDay> = emptyList(),
    /** Teams of the selected stage the matches can be filtered by. */
    val teamOptions: List<TeamOption> = emptyList(),
    /** The team whose matches are shown, or null for all teams. */
    val teamFilter: Team? = null,
    /** [teamFilter]'s place and record in the selected stage, e.g. "2nd · 5-0"; null when unranked there. */
    val teamFilterStanding: String? = null,
    /** Zone of the match times and days. */
    val zoneId: ZoneId = ZoneOffset.UTC,
) {
    /**
     * Name of the selected league, or a neutral label until the leagues load. A leagues retry keeps
     * the loaded list, so a name once shown stays while it reloads.
     */
    val selectedLeagueLabel: String
        get() = selectedLeague?.name ?: "League"
    val selectedLeague: League? get() = leagues.firstOrNull { it.id == selectedLeagueId }
    val selectedTournament: Tournament? get() = tournaments.firstOrNull { it.id == selectedTournamentId }
}

/** What a ranked stage shows: its ranking table or its matches. */
enum class StageView(val label: String) { TABLE("Table"), MATCHES("Matches") }

/**
 * Loads the leagues for the picker and, independently, the selected league's tournaments followed
 * by the standings of the selected tournament. A leagues failure only affects the picker. Every
 * selection change cancels the in-flight load it supersedes, so stale results never overwrite
 * newer ones. The selected league and tournament live in [savedStateHandle], so they survive process
 * death: a restored tournament wins over the default pick while it is still in the loaded list.
 *
 * The user's picks are also saved in [preferences] to survive a fresh launch. When both sources
 * exist, [savedStateHandle] wins: it holds the latest selection of this very session (a process-death
 * restore), while preferences only seed a launch whose handle is empty. The stored selection is read
 * before the first request, so no data is loaded for the default and then reloaded. A stored league
 * missing from the leagues list is dropped, and a stored tournament missing from its league falls
 * back to the default pick. Preferences failures are ignored: the screen works with the defaults.
 *
 * The leagues are requested once, right after the stored value is read, and that same request both
 * feeds the picker and validates the stored league. Validation waits for it at most
 * [LEAGUES_VALIDATION_TIMEOUT]: when the list fails or is slower than that, the stored league is
 * trusted instead, so a slow leagues endpoint delays the standings by a bounded time. The price is
 * that a league that no longer exists is then requested once and shows its error until the user picks
 * another one. A pick made while the stored selection is being restored wins over it, and every
 * load started by init or by such a pick runs once.
 *
 * [refresh] (pull to refresh) asks again for the standings of the selected tournament and keeps the rows on
 * screen meanwhile; a failure leaves them as they were. Any other load of the standings supersedes it.
 *
 * The standings show one stage at a time ([selectStage]); a tournament opens on [defaultStageIndex], or on
 * the stage picked for it before, kept in [savedStateHandle]. A ranked stage of a tournament with dates also
 * lists its matches ([StageView.MATCHES]): the standings carry none, so they come from the league's schedule
 * ([loadEventsBetween], then [regularStageMatches]), loaded the first time that view opens for a tournament
 * and kept in memory per tournament. [selectTeamFilter] narrows them to one team, in memory. Changing the
 * tournament or the league goes back to its default stage, the table and all teams; a refresh keeps them
 * and reloads the matches when they were loaded.
 */
class StandingsViewModel(
    private val repository: StandingsRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val scheduleRepository: ScheduleRepository,
    private val zoneId: ZoneId = clock.zone,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    defaultLeagueId: String = DEFAULT_LEAGUE_ID,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StandingsUiState(selectedLeagueId = savedStateHandle[KEY_LEAGUE_ID] ?: defaultLeagueId, zoneId = zoneId)
    )
    val uiState: StateFlow<StandingsUiState> = _uiState.asStateFlow()

    private var leaguesJob: Job? = null
    private var tournamentsJob: Job? = null
    private var standingsJob: Job? = null
    private var matchesJob: Job? = null

    /** The standings of the selected tournament once loaded, null meanwhile; [presented] derives the shown stage from it. */
    private var standings: Standings? = null

    /** Regular stage matches of each tournament whose Matches view was opened, by tournament id. */
    private val matchesByTournament = mutableMapOf<String, List<ScheduleEvent>>()

    /** True once the stored selection was applied or superseded (a user pick, or nothing usable stored). */
    private var selectionRestored = false

    /** True once a tournaments load was started, by init or by a pick made before init resumed. */
    private var tournamentsRequested = false

    init {
        refreshNoSpoilers()
        refreshFavorites()
        viewModelScope.launch {
            val stored = readStoredSelection()
            val leagues = CompletableDeferred<List<League>?>()
            fetchLeagues(leagues)
            if (stored != null) restoreIfListed(stored, leagues)
            selectionRestored = true
            if (!tournamentsRequested) loadTournaments()
        }
    }

    /**
     * Reads the no-spoilers mode, which Schedule changes; the screen calls it every time it comes back, so a
     * toggle made meanwhile shows up. An unreadable preference keeps the current value (off at first).
     */
    fun refreshNoSpoilers() {
        viewModelScope.launch {
            try {
                preferences.getNoSpoilers()?.let { stored -> _uiState.update { it.copy(noSpoilers = stored) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing with the current mode.
            }
        }
    }

    /**
     * Reads the favorite teams, which are starred in a team's profile; the screen calls it every time it comes
     * back. An unreadable preference keeps the current ones (none at first).
     */
    fun refreshFavorites() {
        viewModelScope.launch {
            try {
                preferences.getFavoriteTeams()?.let { stored ->
                    val names = favoriteNamesOf(stored)
                    _uiState.update { if (it.favoriteNames == names) it else it.copy(favoriteNames = names).presented() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing the current favorites.
            }
        }
    }

    fun selectLeague(leagueId: String) {
        if (leagueId == _uiState.value.selectedLeagueId && selectionRestored) return
        selectionRestored = true
        _uiState.update { it.copy(selectedLeagueId = leagueId) }
        savedStateHandle[KEY_LEAGUE_ID] = leagueId
        savedStateHandle.remove<String>(KEY_TOURNAMENT_ID)
        save(StandingsSelection(leagueId, tournamentId = null))
        loadTournaments()
    }

    fun selectTournament(tournamentId: String) {
        if (tournamentId == _uiState.value.selectedTournamentId) return
        selectionRestored = true
        _uiState.update { it.copy(selectedTournamentId = tournamentId) }
        savedStateHandle[KEY_TOURNAMENT_ID] = tournamentId
        save(StandingsSelection(_uiState.value.selectedLeagueId, tournamentId))
        // Before the first tournaments load there is no list yet: that load picks the saved tournament.
        if (tournamentsRequested) loadStandings() else loadTournaments()
    }

    /**
     * Shows [tournamentId] of [leagueId], as when the user picks that league and then that tournament, but in
     * one step (a team profile opens one this way). It is saved like a manual pick and wins over a stored
     * selection still being restored. Nothing happens when that tournament is already the loaded selection;
     * a tournament missing from its league falls back to the default pick, as a restored one does.
     */
    fun openTournament(leagueId: String, tournamentId: String) {
        val state = _uiState.value
        if (selectionRestored && tournamentsRequested && leagueId == state.selectedLeagueId &&
            tournamentId == state.selectedTournamentId
        ) return
        selectionRestored = true
        _uiState.update { it.copy(selectedLeagueId = leagueId) }
        savedStateHandle[KEY_LEAGUE_ID] = leagueId
        savedStateHandle[KEY_TOURNAMENT_ID] = tournamentId
        save(StandingsSelection(leagueId, tournamentId))
        loadTournaments()
    }

    /** Shows the stage with [key], one of [StandingsUiState.stageTabs], and remembers it for this tournament. */
    fun selectStage(key: String) {
        val state = _uiState.value
        val tournamentId = state.selectedTournamentId ?: return
        if (key == state.selectedStageKey || state.stageTabs.none { it.key == key }) return
        saveStage(tournamentId, key)
        _uiState.update { it.copy(selectedStageKey = key).presented() }
        loadMatchesIfShown()
    }

    /** Table or Matches; the matches of a tournament load the first time they are shown. */
    fun selectStageView(view: StageView) {
        if (view == _uiState.value.stageView) return
        _uiState.update { it.copy(stageView = view) }
        loadMatchesIfShown()
    }

    fun retryMatches() = loadMatches()

    /** Shows only the matches [team] plays, or every match with null; no request is made. */
    fun selectTeamFilter(team: Team?) {
        _uiState.update { it.copy(teamFilter = team).presented() }
    }

    /** Re-fetches only the leagues; tournaments and standings are left as they are. */
    fun retryLeagues() = fetchLeagues()

    fun retryTournaments() = loadTournaments()

    fun retryStandings() = loadStandings()

    /**
     * Pull to refresh: loads the shown standings again without clearing them. Only while standings (or their
     * empty state) are shown and no refresh is already running; a failure changes nothing.
     */
    fun refresh() {
        val state = _uiState.value
        val tournamentId = state.selectedTournamentId ?: return
        if (state.isRefreshing || state.tournamentsStatus != LoadStatus.Content) return
        if (state.standingsStatus != LoadStatus.Content && state.standingsStatus != LoadStatus.Empty) return
        _uiState.update { it.copy(isRefreshing = true) }
        // Shares the standings job, so a retry or a selection change cancels it like any other standings load.
        standingsJob = viewModelScope.launch {
            try {
                publishStandings(tournamentId, repository.refreshStandings(tournamentId))
                if (tournamentId in matchesByTournament) refreshMatches(tournamentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The standings stay as they were.
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** The stored selection, or null when the handle already holds one, nothing is stored or the read fails. */
    private suspend fun readStoredSelection(): StandingsSelection? {
        if (selectionRestored || savedStateHandle.contains(KEY_LEAGUE_ID)) return null
        return try {
            preferences.getStandingsSelection()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Seeds the selection from [stored] through the saved state handle, which the tournament load
     * already reads, once its league is confirmed in [leagues] (or the list is unavailable). A pick
     * made meanwhile wins.
     */
    private suspend fun restoreIfListed(stored: StandingsSelection, leagues: CompletableDeferred<List<League>?>) {
        if (selectionRestored) return
        val loaded = withTimeoutOrNull(LEAGUES_VALIDATION_TIMEOUT) { leagues.await() }
        if (selectionRestored) return
        if (loaded != null && loaded.none { it.id == stored.leagueId }) return
        savedStateHandle[KEY_LEAGUE_ID] = stored.leagueId
        savedStateHandle[KEY_TOURNAMENT_ID] = stored.tournamentId
        _uiState.update { it.copy(selectedLeagueId = stored.leagueId) }
    }

    private fun save(selection: StandingsSelection) {
        viewModelScope.launch {
            try {
                preferences.saveStandingsSelection(selection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not remembering the selection is better than failing the pick.
            }
        }
    }

    /** [result] receives the loaded leagues, or null when the request fails or is superseded. */
    private fun fetchLeagues(result: CompletableDeferred<List<League>?>? = null) {
        leaguesJob?.cancel()
        _uiState.update { it.copy(leaguesStatus = LoadStatus.Loading) }
        leaguesJob = viewModelScope.launch {
            try {
                val leagues = leaguesRepository.getLeagues()
                result?.complete(leagues)
                _uiState.update {
                    it.copy(
                        leagues = leagues,
                        leaguesStatus = if (leagues.isEmpty()) LoadStatus.Empty else LoadStatus.Content,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(leaguesStatus = LoadStatus.Error(e.toUserMessage())) }
            }
        }.also { job -> job.invokeOnCompletion { result?.complete(null) } }
    }

    private fun loadTournaments() {
        tournamentsRequested = true
        tournamentsJob?.cancel()
        standingsJob?.cancel()
        _uiState.update {
            it.copy(
                tournamentsStatus = LoadStatus.Loading,
                tournaments = emptyList(),
                selectedTournamentId = null,
                standingsStatus = LoadStatus.Loading,
                rows = emptyList(),
                isRefreshing = false,
            ).withoutStage()
        }
        standings = null
        matchesJob?.cancel()
        val leagueId = _uiState.value.selectedLeagueId
        savedStateHandle[KEY_LEAGUE_ID] = leagueId
        tournamentsJob = viewModelScope.launch {
            try {
                val tournaments = repository.getTournaments(leagueId)
                val restoredId = savedStateHandle.get<String>(KEY_TOURNAMENT_ID)
                val selected = tournaments.firstOrNull { it.id == restoredId }
                    ?: pickDefaultTournament(tournaments, LocalDate.now(clock))
                savedStateHandle[KEY_TOURNAMENT_ID] = selected?.id
                _uiState.update {
                    it.copy(
                        tournamentsStatus = if (tournaments.isEmpty()) LoadStatus.Empty else LoadStatus.Content,
                        tournaments = tournaments,
                        selectedTournamentId = selected?.id,
                    )
                }
                if (selected != null) loadStandings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(tournamentsStatus = LoadStatus.Error(e.toUserMessage())) }
            }
        }
    }

    private fun loadStandings() {
        standingsJob?.cancel()
        val tournamentId = _uiState.value.selectedTournamentId ?: return
        matchesJob?.cancel()
        standings = null
        _uiState.update { it.copy(standingsStatus = LoadStatus.Loading, rows = emptyList(), isRefreshing = false).withoutStage() }
        standingsJob = viewModelScope.launch {
            try {
                publishStandings(tournamentId, repository.getStandings(tournamentId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (it.selectedTournamentId != tournamentId) it
                    else it.copy(standingsStatus = LoadStatus.Error(e.toUserMessage()))
                }
            }
        }
    }

    /**
     * Shows [loaded] for [tournamentId], unless another tournament is selected meanwhile. The stage stays the
     * one on screen (a refresh), else the one saved for this tournament, else [defaultStageIndex]; a key the
     * standings no longer have counts as none.
     */
    private fun publishStandings(tournamentId: String, loaded: Standings) {
        // Defensive: the job is cancelled on change, but never publish for another selection.
        if (_uiState.value.selectedTournamentId != tournamentId) return
        standings = loaded
        val tabs = stageTabs(loaded)
        val saved = savedStateHandle.get<String>(KEY_STAGE).takeIf { savedStateHandle.get<String>(KEY_STAGE_TOURNAMENT) == tournamentId }
        val key = listOf(_uiState.value.selectedStageKey, saved).firstOrNull { key -> tabs.any { it.key == key } }
            ?: tabs.getOrNull(defaultStageIndex(loaded.stages))?.key
        if (key != null) saveStage(tournamentId, key)
        _uiState.update {
            it.copy(
                standingsStatus = if (loaded.stages.isEmpty()) LoadStatus.Empty else LoadStatus.Content,
                selectedStageKey = key,
            ).presented()
        }
    }

    private fun saveStage(tournamentId: String, key: String) {
        savedStateHandle[KEY_STAGE] = key
        savedStateHandle[KEY_STAGE_TOURNAMENT] = tournamentId
    }

    /** Loads the matches when the Matches view is on screen and this tournament's are neither kept nor loading. */
    private fun loadMatchesIfShown() {
        val state = _uiState.value
        val tournamentId = state.selectedTournamentId ?: return
        if (state.stageView != StageView.MATCHES || !state.canShowMatches) return
        val cached = matchesByTournament[tournamentId]
        when {
            cached != null -> _uiState.update { it.copy(matchesStatus = statusOf(cached)).presented() }
            matchesJob?.isActive != true -> loadMatches()
        }
    }

    private fun loadMatches() {
        matchesJob?.cancel()
        val tournamentId = _uiState.value.selectedTournamentId ?: return
        _uiState.update { it.copy(matchesStatus = LoadStatus.Loading) }
        matchesJob = viewModelScope.launch {
            try {
                val events = fetchMatches(tournamentId) ?: return@launch
                matchesByTournament[tournamentId] = events
                _uiState.update {
                    if (it.selectedTournamentId != tournamentId) it else it.copy(matchesStatus = statusOf(events)).presented()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (it.selectedTournamentId != tournamentId) it else it.copy(matchesStatus = LoadStatus.Error(e.toUserMessage()))
                }
            }
        }
    }

    /** Part of a refresh: loads the kept matches again, leaving them as they are when that fails. */
    private suspend fun refreshMatches(tournamentId: String) {
        val events = try {
            fetchMatches(tournamentId) ?: return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        matchesByTournament[tournamentId] = events
        _uiState.update {
            if (it.selectedTournamentId != tournamentId) it else it.copy(matchesStatus = statusOf(events)).presented()
        }
    }

    /** The regular stage matches of the selected tournament, null when it has no dates or no standings yet. */
    private suspend fun fetchMatches(tournamentId: String): List<ScheduleEvent>? {
        val state = _uiState.value
        val tournament = state.tournaments.firstOrNull { it.id == tournamentId } ?: return null
        val start = tournament.startDate ?: return null
        val end = tournament.endDate ?: return null
        val loaded = standings ?: return null
        val events = scheduleRepository.loadEventsBetween(state.selectedLeagueId, start, end, zoneId)
        return regularStageMatches(events, tournament, loaded, zoneId)
    }

    private fun statusOf(events: List<ScheduleEvent>): LoadStatus = if (events.isEmpty()) LoadStatus.Empty else LoadStatus.Content

    /**
     * This state with everything derived from the selected stage recomputed: its rows, the tabs, whether it
     * lists matches, the team options and the kept matches by day, narrowed to [StandingsUiState.teamFilter].
     */
    private fun StandingsUiState.presented(): StandingsUiState {
        val loaded = standings
        val tabs = loaded?.let(::stageTabs).orEmpty()
        val stage = loaded?.stages?.getOrNull(tabs.indexOfFirst { it.key == selectedStageKey })
        val tournament = selectedTournament
        val events = selectedTournamentId?.let { matchesByTournament[it] }.orEmpty()
        val filter = teamFilter
        val shown = if (filter == null) events else events.filter { it.involvesTeam(filter) }
        return copy(
            stageTabs = tabs,
            rows = stage?.let { buildStandingsRows(Standings(listOf(it))) }.orEmpty(),
            canShowMatches = stage?.isRanked == true && tournament?.startDate != null && tournament.endDate != null,
            teamOptions = stage?.let { stageTeamOptions(it, favoriteNames) }.orEmpty(),
            teamFilterStanding = filter?.let { team -> stage?.let { stageRankingOf(it, team) } }?.let(::stageStandingLine),
            matchDays = groupEventsByDay(shown, zoneId),
        )
    }

    /** A new tournament starts on its table, with all teams and no stage until its standings load. */
    private fun StandingsUiState.withoutStage(): StandingsUiState = copy(
        stageTabs = emptyList(),
        selectedStageKey = null,
        stageView = StageView.TABLE,
        canShowMatches = false,
        matchesStatus = LoadStatus.Loading,
        matchDays = emptyList(),
        teamOptions = emptyList(),
        teamFilter = null,
        teamFilterStanding = null,
    )

    companion object {
        /** LCK: a round-robin league, so the default view shows real standings tables. */
        const val DEFAULT_LEAGUE_ID = "98767991310872058"

        /** How long a stored league waits for the leagues list to confirm it before being trusted. */
        internal val LEAGUES_VALIDATION_TIMEOUT: Duration = 3.seconds

        const val KEY_LEAGUE_ID = "standings.leagueId"
        const val KEY_TOURNAMENT_ID = "standings.tournamentId"
        const val KEY_STAGE = "standings.stage"
        const val KEY_STAGE_TOURNAMENT = "standings.stageTournamentId"
    }
}
