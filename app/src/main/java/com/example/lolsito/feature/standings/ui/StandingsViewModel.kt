package com.example.lolsito.feature.standings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.model.KnownLeagues
import com.example.lolsito.core.model.League
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.feature.standings.domain.pickDefaultTournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface LoadStatus {
    data object Loading : LoadStatus
    data object Content : LoadStatus
    data object Empty : LoadStatus
    data class Error(val message: String) : LoadStatus
}

data class StandingsUiState(
    val leagues: List<League> = emptyList(),
    val leaguesStatus: LoadStatus = LoadStatus.Loading,
    val selectedLeagueId: String,
    val tournamentsStatus: LoadStatus = LoadStatus.Loading,
    val tournaments: List<Tournament> = emptyList(),
    val selectedTournamentId: String? = null,
    val standingsStatus: LoadStatus = LoadStatus.Loading,
    val rows: List<StandingsRow> = emptyList(),
) {
    /** Name of the selected league; a known name (or a generic label) while the leagues are not loaded. */
    val selectedLeagueLabel: String
        get() = selectedLeague?.name ?: KnownLeagues.nameFor(selectedLeagueId) ?: "League"
    val selectedLeague: League? get() = leagues.firstOrNull { it.id == selectedLeagueId }
    val selectedTournament: Tournament? get() = tournaments.firstOrNull { it.id == selectedTournamentId }
}

/**
 * Loads the leagues for the picker and, independently, the selected league's tournaments followed
 * by the standings of the selected tournament. A leagues failure only affects the picker. Every
 * selection change cancels the in-flight load it supersedes, so stale results never overwrite
 * newer ones.
 */
class StandingsViewModel(
    private val repository: StandingsRepository,
    private val loadLeagues: suspend () -> List<League>,
    private val clock: Clock,
    defaultLeagueId: String = DEFAULT_LEAGUE_ID,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandingsUiState(selectedLeagueId = defaultLeagueId))
    val uiState: StateFlow<StandingsUiState> = _uiState.asStateFlow()

    private var leaguesJob: Job? = null
    private var tournamentsJob: Job? = null
    private var standingsJob: Job? = null

    init {
        fetchLeagues()
        loadTournaments()
    }

    fun selectLeague(leagueId: String) {
        if (leagueId == _uiState.value.selectedLeagueId) return
        _uiState.update { it.copy(selectedLeagueId = leagueId) }
        loadTournaments()
    }

    fun selectTournament(tournamentId: String) {
        if (tournamentId == _uiState.value.selectedTournamentId) return
        _uiState.update { it.copy(selectedTournamentId = tournamentId) }
        loadStandings()
    }

    /** Re-fetches only the leagues; tournaments and standings are left as they are. */
    fun retryLeagues() = fetchLeagues()

    fun retryTournaments() = loadTournaments()

    fun retryStandings() = loadStandings()

    private fun fetchLeagues() {
        leaguesJob?.cancel()
        _uiState.update { it.copy(leaguesStatus = LoadStatus.Loading) }
        leaguesJob = viewModelScope.launch {
            try {
                val leagues = loadLeagues()
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
        }
    }

    private fun loadTournaments() {
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
        tournamentsJob = viewModelScope.launch {
            try {
                val tournaments = repository.getTournaments(leagueId)
                val selected = pickDefaultTournament(tournaments, LocalDate.now(clock))
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
    }
}
