package com.example.lolsito.feature.teams.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.mergeEvents
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.feature.teams.domain.buildTeamMatches
import com.example.lolsito.feature.teams.domain.homeLeagueId
import com.example.lolsito.feature.teams.domain.isPlayedBy
import com.example.lolsito.feature.teams.domain.teamScheduleLeagueIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZoneOffset

data class TeamProfileUiState(
    /** The team itself; [LoadStatus.Empty] when the API knows no such team. */
    val status: LoadStatus = LoadStatus.Loading,
    val profile: TeamProfile? = null,
    /** The team's matches, loaded once the profile is known. */
    val matchesStatus: LoadStatus = LoadStatus.Loading,
    /** Unstarted and live matches by day, soonest first. */
    val upcomingDays: List<ScheduleDay> = emptyList(),
    /** Finished matches by day, most recent first. */
    val previousDays: List<ScheduleDay> = emptyList(),
    val canLoadOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    /** Last "Load older" failure, shown next to the button until it is tried again. */
    val olderError: String? = null,
    /** The no-spoilers mode set in Schedule: previous results are hidden the same way. */
    val noSpoilers: Boolean = false,
    /** The star: whether the team is one of the user's favorites. Remembered between launches. */
    val isFavorite: Boolean = false,
    val zoneId: ZoneId = ZoneOffset.UTC,
)

/**
 * A team's profile: the team and its roster from [teamsRepository], then its matches. The schedule
 * cannot filter by team, so the matches are the events of the team's home league plus the
 * international leagues (see [teamScheduleLeagueIds]) played by a team of the same name. The home
 * league is found by name among the leagues; when that fails, [originLeagueId] (the league the team
 * was opened from) stands in for it. "Load older" pages back with the same filter.
 *
 * The no-spoilers mode is read before any match is shown; an unreadable preference counts as off.
 *
 * The star ([toggleFavorite]) marks or unmarks the team in the stored favorites. It flips at once; a
 * failed read or save is ignored (an unreadable list is never overwritten), and an unreadable list
 * counts as "not a favorite" when the profile opens.
 */
class TeamProfileViewModel(
    private val teamId: String,
    private val originLeagueId: String?,
    private val teamsRepository: TeamsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamProfileUiState(zoneId = zoneId))
    val uiState: StateFlow<TeamProfileUiState> = _uiState.asStateFlow()

    /** The team's events only, chronological. */
    private var events: List<ScheduleEvent> = emptyList()
    private var leagueIds: List<String> = emptyList()
    private var olderToken: String? = null
    private var loadJob: Job? = null
    private var pagingJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /** Does nothing until the profile is known. */
    fun toggleFavorite() {
        val profile = _uiState.value.profile ?: return
        val on = !_uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = on) }
        viewModelScope.launch {
            try {
                val others = preferences.getFavoriteTeams().orEmpty().filterNot { it.id == profile.id }
                preferences.saveFavoriteTeams(if (on) others + profile.toFavorite() else others)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not remembering the star is better than failing the toggle.
            }
        }
    }

    fun loadOlder() {
        val token = olderToken ?: return
        val profile = _uiState.value.profile ?: return
        if (pagingJob?.isActive == true || loadJob?.isActive == true) return
        val filter = leagueIds
        _uiState.update { it.copy(isLoadingOlder = true, olderError = null) }
        pagingJob = viewModelScope.launch {
            try {
                val page = scheduleRepository.getSchedule(filter, token)
                events = mergeEvents(events, page.events.filter { it.isPlayedBy(profile.name) })
                olderToken = page.olderToken
                publishMatches(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingOlder = false, olderError = e.toMessage()) }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        pagingJob?.cancel()
        events = emptyList()
        olderToken = null
        _uiState.update {
            TeamProfileUiState(noSpoilers = it.noSpoilers, zoneId = zoneId)
        }
        loadJob = viewModelScope.launch {
            val noSpoilers = readNoSpoilers()
            _uiState.update { it.copy(noSpoilers = noSpoilers) }
            val profile = try {
                teamsRepository.getTeam(teamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(status = LoadStatus.Error(e.toMessage()), matchesStatus = LoadStatus.Empty) }
                return@launch
            }
            if (profile == null) {
                _uiState.update { it.copy(status = LoadStatus.Empty, matchesStatus = LoadStatus.Empty) }
                return@launch
            }
            val isFavorite = readIsFavorite(profile.id)
            _uiState.update { it.copy(status = LoadStatus.Content, profile = profile, isFavorite = isFavorite) }
            try {
                leagueIds = teamScheduleLeagueIds(resolveHomeLeagueId(profile) ?: originLeagueId)
                val page = scheduleRepository.getSchedule(leagueIds, pageToken = null)
                events = mergeEvents(emptyList(), page.events.filter { it.isPlayedBy(profile.name) })
                olderToken = page.olderToken
                publishMatches(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(matchesStatus = LoadStatus.Error(e.toMessage())) }
            }
        }
    }

    /** Null when the leagues cannot be loaded or none has the home league's name. */
    private suspend fun resolveHomeLeagueId(profile: TeamProfile): String? {
        val leagues = try {
            leaguesRepository.getLeagues()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return homeLeagueId(leagues, profile.homeLeagueName)
    }

    private suspend fun readIsFavorite(id: String): Boolean = try {
        preferences.getFavoriteTeams().orEmpty().any { it.id == id }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun TeamProfile.toFavorite() = FavoriteTeam(id, slug, name, code, imageUrl)

    private suspend fun readNoSpoilers(): Boolean = try {
        preferences.getNoSpoilers() ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** No match yet but an older page to load stays Content, so the user can page back. */
    private fun publishMatches(profile: TeamProfile) {
        val matches = buildTeamMatches(events, profile.name, zoneId)
        _uiState.update {
            it.copy(
                matchesStatus = if (events.isEmpty() && olderToken == null) LoadStatus.Empty else LoadStatus.Content,
                upcomingDays = matches.upcoming,
                previousDays = matches.previous,
                canLoadOlder = olderToken != null,
                isLoadingOlder = false,
            )
        }
    }

    private fun Exception.toMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
}
