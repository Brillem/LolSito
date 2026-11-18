package com.example.lolsito.feature.teams.ui

import com.example.lolsito.core.error.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.core.preferences.FavoriteTeam
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.feature.match.domain.FormEntry
import com.example.lolsito.feature.match.domain.recentForm
import com.example.lolsito.feature.reminders.domain.ReminderSyncTrigger
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.mergeEvents
import com.example.lolsito.feature.standings.domain.StandingsRepository
import com.example.lolsito.feature.standings.ui.label
import com.example.lolsito.feature.teams.domain.RosterSplit
import com.example.lolsito.feature.teams.domain.TeamLineupRepository
import com.example.lolsito.feature.teams.domain.TeamProfile
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.feature.teams.domain.buildTeamMatches
import com.example.lolsito.feature.teams.domain.homeLeagueId
import com.example.lolsito.feature.teams.domain.findLineup
import com.example.lolsito.feature.teams.domain.isPlayedBy
import com.example.lolsito.feature.teams.domain.nextMatch
import com.example.lolsito.feature.teams.domain.recentTeamTournaments
import com.example.lolsito.feature.teams.domain.teamStandingLine
import com.example.lolsito.feature.teams.domain.lineupCandidateMatchIds
import com.example.lolsito.feature.teams.domain.splitRoster
import com.example.lolsito.feature.teams.domain.teamScheduleLeagueIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A row of "Recent tournaments": [result] is how far the team got, null when unknown or hidden (no-spoilers mode). */
data class RecentTournament(
    val leagueId: String,
    val tournamentId: String,
    val name: String,
    val result: String?,
    val leagueImageUrl: String?,
)

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
    /**
     * The roster split into the players of the team's last game and the rest; null until known, and
     * for good when it cannot be found. The roster itself is always in [profile].
     */
    val lineup: RosterSplit? = null,
    /**
     * "3rd in LCK · 12-4": the team's place and record in its home league's current tournament; null
     * until known, and for good when it cannot be (no ranking, no home league, failed request).
     */
    val standing: String? = null,
    /** The team's last finished series, most recent first (see [recentForm]); empty with no-spoilers mode on. */
    val form: List<FormEntry> = emptyList(),
    /** The team's soonest unstarted or live match, which also appears in [upcomingDays]; null when there is none. */
    val nextMatch: ScheduleEvent? = null,
    /**
     * The tournaments the team recently played, most recent first (see [recentTeamTournaments]); empty until
     * known, and for good when there are none or the requests fail.
     */
    val recentTournaments: List<RecentTournament> = emptyList(),
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
 * counts as "not a favorite" when the profile opens. A saved change asks [reminderSync] for a reminder
 * sync, since the match reminders follow the favorites.
 *
 * Starters are secondary: once the first page of matches is known, the lineup of the team's last
 * played game is looked up in the background ([findLineup] over the newest finished matches) and
 * the roster is split with it. It never delays the roster or the matches, and any failure leaves the
 * profile as it is, with no error.
 *
 * The standing line is just as secondary: from the moment the profile is known, the team's ranking in the
 * home league's current tournament is looked up on its own (the origin league does not stand in for the home
 * league), concurrently with the matches. Any failure or missing ranking leaves [TeamProfileUiState.standing]
 * null, with no error and no effect on the rest of the profile.
 *
 * The recent tournaments are the same kind of extra: loaded on their own right after the no-spoilers mode and
 * the profile are known, concurrently with the rest, and silent when they fail. With no-spoilers mode on, only a
 * tournament in progress shows its result.
 *
 * The recent form and the next match come from the same events as the match lists, with no extra request.
 */
class TeamProfileViewModel(
    private val teamId: String,
    private val originLeagueId: String?,
    private val teamsRepository: TeamsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val preferences: UserPreferences,
    private val lineupRepository: TeamLineupRepository,
    private val standingsRepository: StandingsRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val reminderSync: ReminderSyncTrigger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamProfileUiState(zoneId = zoneId))
    val uiState: StateFlow<TeamProfileUiState> = _uiState.asStateFlow()

    /** The team's events only, chronological. */
    private var events: List<ScheduleEvent> = emptyList()
    private var leagueIds: List<String> = emptyList()
    private var olderToken: String? = null
    private var loadJob: Job? = null
    private var pagingJob: Job? = null
    private var lineupJob: Job? = null
    private var standingJob: Job? = null
    private var tournamentsJob: Job? = null

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
                // The match reminders follow the favorites.
                reminderSync.requestSync()
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
                _uiState.update { it.copy(isLoadingOlder = false, olderError = e.toUserMessage()) }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        pagingJob?.cancel()
        lineupJob?.cancel()
        standingJob?.cancel()
        tournamentsJob?.cancel()
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
                _uiState.update { it.copy(status = LoadStatus.Error(e.toUserMessage()), matchesStatus = LoadStatus.Empty) }
                return@launch
            }
            if (profile == null) {
                _uiState.update { it.copy(status = LoadStatus.Empty, matchesStatus = LoadStatus.Empty) }
                return@launch
            }
            val isFavorite = readIsFavorite(profile.id)
            _uiState.update { it.copy(status = LoadStatus.Content, profile = profile, isFavorite = isFavorite) }
            loadStanding(profile)
            loadRecentTournaments(profile, noSpoilers)
            try {
                leagueIds = teamScheduleLeagueIds(resolveHomeLeagueId(profile) ?: originLeagueId)
                val page = scheduleRepository.getSchedule(leagueIds, pageToken = null)
                events = mergeEvents(emptyList(), page.events.filter { it.isPlayedBy(profile.name) })
                olderToken = page.olderToken
                publishMatches(profile)
                loadLineup(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(matchesStatus = LoadStatus.Error(e.toUserMessage())) }
            }
        }
    }

    /** Runs on its own, so the matches and "Load older" never wait for it. */
    private fun loadLineup(profile: TeamProfile) {
        val candidates = lineupCandidateMatchIds(events)
        lineupJob = viewModelScope.launch {
            val ids = findLineup(candidates) { lineupRepository.getLineup(it, profile.id) } ?: return@launch
            val split = splitRoster(profile.roster, ids) ?: return@launch
            _uiState.update { it.copy(lineup = split) }
        }
    }

    /** Runs on its own, concurrently with the matches; every failure is silent. */
    private fun loadStanding(profile: TeamProfile) {
        standingJob = viewModelScope.launch {
            try {
                val leagues = leaguesRepository.getLeagues()
                val leagueId = homeLeagueId(leagues, profile.homeLeagueName) ?: return@launch
                val leagueName = profile.homeLeagueName?.trim() ?: return@launch
                val line = standingsRepository.teamStandingLine(
                    leagueId, leagueName, profile.id, profile.name, LocalDate.now(clock),
                ) ?: return@launch
                _uiState.update { it.copy(standing = line) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The standing line is a nicety: without it the profile is simply as before.
            }
        }
    }

    /** Runs on its own, concurrently with the matches; every failure is silent. */
    private fun loadRecentTournaments(profile: TeamProfile, noSpoilers: Boolean) {
        tournamentsJob = viewModelScope.launch {
            try {
                val leagues = try {
                    leaguesRepository.getLeagues()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val found = standingsRepository.recentTeamTournaments(
                    homeLeagueId = homeLeagueId(leagues, profile.homeLeagueName),
                    teamId = profile.id,
                    teamName = profile.name,
                    today = LocalDate.now(clock),
                    hideResults = noSpoilers,
                )
                val rows = found.map { entry ->
                    RecentTournament(
                        leagueId = entry.leagueId,
                        tournamentId = entry.tournament.id,
                        name = entry.tournament.label,
                        result = entry.result,
                        leagueImageUrl = leagues.firstOrNull { it.id == entry.leagueId }?.imageUrl,
                    )
                }
                _uiState.update { it.copy(recentTournaments = rows) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The section is a nicety: without it the profile is simply as before.
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
                form = if (it.noSpoilers) emptyList() else recentForm(events, profile.name, currentMatchId = ""),
                nextMatch = nextMatch(events, profile.name),
                upcomingDays = matches.upcoming,
                previousDays = matches.previous,
                canLoadOlder = olderToken != null,
                isLoadingOlder = false,
            )
        }
    }
}
