package com.example.lolsito.feature.teams.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.model.League
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.feature.teams.domain.currentTeams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
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
 */
class TeamsViewModel(
    private val standingsRepository: StandingsRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
    defaultLeagueId: String = DEFAULT_LEAGUE_ID,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamsUiState(selectedLeagueId = defaultLeagueId))
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    private var leaguesJob: Job? = null
    private var teamsJob: Job? = null

    init {
        refreshFavorites()
        loadLeagues()
        loadTeams()
    }

    fun selectLeague(leagueId: String) {
        if (leagueId == _uiState.value.selectedLeagueId) return
        _uiState.update { it.copy(selectedLeagueId = leagueId) }
        loadTeams()
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
                _uiState.update { it.copy(leaguesStatus = LoadStatus.Error(e.toMessage())) }
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
                _uiState.update { it.copy(status = LoadStatus.Error(e.toMessage())) }
            }
        }
    }

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

    companion object {
        const val DEFAULT_LEAGUE_ID = "98767991310872058" // LCK
    }
}
