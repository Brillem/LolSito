package com.example.lolsito.feature.standings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.StandingsSelection
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
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
 */
class StandingsViewModel(
    private val repository: StandingsRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    defaultLeagueId: String = DEFAULT_LEAGUE_ID,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StandingsUiState(selectedLeagueId = savedStateHandle[KEY_LEAGUE_ID] ?: defaultLeagueId)
    )
    val uiState: StateFlow<StandingsUiState> = _uiState.asStateFlow()

    private var leaguesJob: Job? = null
    private var tournamentsJob: Job? = null
    private var standingsJob: Job? = null

    /** True once the stored selection was applied or superseded (a user pick, or nothing usable stored). */
    private var selectionRestored = false

    /** True once a tournaments load was started, by init or by a pick made before init resumed. */
    private var tournamentsRequested = false

    init {
        refreshNoSpoilers()
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

    /** Re-fetches only the leagues; tournaments and standings are left as they are. */
    fun retryLeagues() = fetchLeagues()

    fun retryTournaments() = loadTournaments()

    fun retryStandings() = loadStandings()

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
                _uiState.update { it.copy(leaguesStatus = LoadStatus.Error(e.toMessage())) }
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
            )
        }
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
                _uiState.update { it.copy(tournamentsStatus = LoadStatus.Error(e.toMessage())) }
            }
        }
    }

    private fun loadStandings() {
        standingsJob?.cancel()
        val tournamentId = _uiState.value.selectedTournamentId ?: return
        _uiState.update { it.copy(standingsStatus = LoadStatus.Loading, rows = emptyList()) }
        standingsJob = viewModelScope.launch {
            try {
                val rows = buildStandingsRows(repository.getStandings(tournamentId))
                _uiState.update {
                    // Defensive: the job is cancelled on change, but never publish for another selection.
                    if (it.selectedTournamentId != tournamentId) it
                    else it.copy(standingsStatus = if (rows.isEmpty()) LoadStatus.Empty else LoadStatus.Content, rows = rows)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (it.selectedTournamentId != tournamentId) it
                    else it.copy(standingsStatus = LoadStatus.Error(e.toMessage()))
                }
            }
        }
    }

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

    companion object {
        /** LCK: a round-robin league, so the default view shows real standings tables. */
        const val DEFAULT_LEAGUE_ID = "98767991310872058"

        /** How long a stored league waits for the leagues list to confirm it before being trusted. */
        internal val LEAGUES_VALIDATION_TIMEOUT: Duration = 3.seconds

        const val KEY_LEAGUE_ID = "standings.leagueId"
        const val KEY_TOURNAMENT_ID = "standings.tournamentId"
    }
}
