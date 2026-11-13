package com.example.lolsito.feature.match.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.polling.MATCH_DETAIL_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.core.leagues.LeaguesRepository
import com.example.lolsito.feature.match.domain.MatchDetailRepository
import com.example.lolsito.core.model.Team
import com.example.lolsito.feature.match.domain.MAX_OLDER_HISTORY_PAGES
import com.example.lolsito.feature.match.domain.MatchHistory
import com.example.lolsito.feature.match.domain.RECENT_FORM_SIZE
import com.example.lolsito.feature.match.domain.buildMatchHistory
import com.example.lolsito.feature.match.domain.matchHistoryLeagueIds
import com.example.lolsito.feature.match.domain.needsOlderHistory
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.ScheduleRepository
import com.example.lolsito.feature.schedule.domain.mergeEvents
import com.example.lolsito.feature.teams.domain.TeamsRepository
import com.example.lolsito.feature.teams.domain.homeLeagueId
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState
    data class Error(val message: String) : MatchDetailUiState
    /** [resultsHidden]: the no-spoilers mode is on and the user has not revealed this series (see [displayedResult]). */
    data class Content(val detail: MatchDetail, val resultsHidden: Boolean = false) : MatchDetailUiState
}

/** The recent form and head to head sections of Match detail, loaded apart from the series. */
sealed interface MatchHistoryUiState {
    /**
     * Nothing to show: the series is not known yet or does not have two teams, or its results are hidden (the
     * no-spoilers mode, until [MatchDetailViewModel.revealResult]). While hidden this is the only state, whatever
     * was loaded, so no result of these sections leaves the view model.
     */
    data object None : MatchHistoryUiState
    data object Loading : MatchHistoryUiState
    /** The history could not be loaded; [MatchDetailViewModel.retryHistory] tries again. */
    data object Error : MatchHistoryUiState
    data class Content(val history: MatchHistory) : MatchHistoryUiState
}

/**
 * Loads one match. While its series is in progress, [pollWhileVisible] reloads it every
 * [MATCH_DETAIL_POLL_INTERVAL] and stops by itself once the series is completed.
 *
 * With the no-spoilers mode on in [preferences] (read once, before the first request; unreadable counts as off) the
 * content starts with its results hidden and [revealResult] shows them. The reveal lives only in this view model,
 * so it covers just this series and ends when the screen is left.
 *
 * The history ([historyState]: recent form of both teams and their head to head) is secondary. It is loaded once,
 * on its own, after the series is first known, so it never delays it, and the polling never reloads it. It comes
 * from the schedule, which cannot filter by team: one request for the home league of each team (found as the team
 * profile does, by the league name [teamsRepository] gives) plus the international leagues, see
 * [matchHistoryLeagueIds]. Only while a team has fewer than [RECENT_FORM_SIZE] finished series are older pages
 * read, at most [MAX_OLDER_HISTORY_PAGES]. A failure only shows in [historyState]; the series stays as it is.
 */
class MatchDetailViewModel(
    private val matchId: String,
    private val repository: MatchDetailRepository,
    private val preferences: UserPreferences,
    private val teamsRepository: TeamsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    private val _historyState = MutableStateFlow<MatchHistoryUiState>(MatchHistoryUiState.None)
    val historyState: StateFlow<MatchHistoryUiState> = _historyState.asStateFlow()

    /** What was loaded, whether or not it may be shown; [historyState] is what the screen gets. */
    private var history: MatchHistoryUiState = MatchHistoryUiState.None

    private var loadJob: Job? = null
    private var historyJob: Job? = null
    private var noSpoilers: Boolean? = null
    private var revealed = false

    init {
        load()
    }

    fun retry() = load()

    /** Loads the history again after it failed; does nothing otherwise. */
    fun retryHistory() {
        if (history != MatchHistoryUiState.Error) return
        val teams = (_uiState.value as? MatchDetailUiState.Content)?.detail?.teams ?: return
        loadHistory(teams)
    }

    /** Shows the result of this series until the screen is left. */
    fun revealResult() {
        revealed = true
        _uiState.update { if (it is MatchDetailUiState.Content) it.copy(resultsHidden = false) else it }
        publishHistory()
    }

    private fun resultsHidden(): Boolean = noSpoilers == true && !revealed

    /**
     * Keeps the content fresh for as long as it is in progress. The screen runs it only while visible,
     * so leaving the screen cancels it. A refresh that fails leaves the content as it was; an initial
     * load that is still running, failed or completed is never polled, and [retry] pauses polling
     * until its own load ends.
     */
    suspend fun pollWhileVisible() {
        val inProgress = uiState.map { (it as? MatchDetailUiState.Content)?.detail?.state == MatchState.IN_PROGRESS }
        pollWhile(MATCH_DETAIL_POLL_INTERVAL, inProgress) { refresh() }
    }

    private suspend fun refresh() {
        val detail = repository.getMatchDetail(matchId)
        // Only replace content: a reload started meanwhile owns the state.
        _uiState.update { if (it is MatchDetailUiState.Content) MatchDetailUiState.Content(detail, resultsHidden()) else it }
    }

    /** Read before the first request, so results never show for a moment; the answer is kept for this screen. */
    private suspend fun readNoSpoilers(): Boolean {
        if (noSpoilers == null) {
            noSpoilers = try {
                preferences.getNoSpoilers() ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
        return resultsHidden()
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = MatchDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val hidden = readNoSpoilers()
                val detail = repository.getMatchDetail(matchId)
                if (historyJob == null) loadHistory(detail.teams)
                MatchDetailUiState.Content(detail, hidden)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MatchDetailUiState.Error(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong")
            }
        }
    }

    /** Runs on its own, so the series never waits for it. A series without two teams has no history. */
    private fun loadHistory(teams: List<Team>) {
        if (teams.size != 2) return
        historyJob?.cancel()
        history = MatchHistoryUiState.Loading
        publishHistory()
        historyJob = viewModelScope.launch {
            history = try {
                MatchHistoryUiState.Content(buildMatchHistory(fetchHistoryEvents(teams), teams, matchId, zoneId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MatchHistoryUiState.Error
            }
            publishHistory()
        }
    }

    private fun publishHistory() {
        _historyState.value = if (resultsHidden()) MatchHistoryUiState.None else history
    }

    /** The first schedule page for both teams, plus older ones only while a team's recent form is incomplete. */
    private suspend fun fetchHistoryEvents(teams: List<Team>): List<ScheduleEvent> {
        val homeLeagueNames = teams.map { team -> team.profileId()?.let { teamsRepository.getTeam(it) }?.homeLeagueName }
        val leagues = if (homeLeagueNames.any { !it.isNullOrBlank() }) leaguesRepository.getLeagues() else emptyList()
        val leagueIds = matchHistoryLeagueIds(homeLeagueNames.map { homeLeagueId(leagues, it) })
        val names = teams.map { it.name }
        var page = scheduleRepository.getSchedule(leagueIds, pageToken = null)
        var events = mergeEvents(emptyList(), page.events)
        var olderPages = 0
        while (olderPages < MAX_OLDER_HISTORY_PAGES && needsOlderHistory(events, names, matchId)) {
            val token = page.olderToken ?: break
            page = scheduleRepository.getSchedule(leagueIds, token)
            events = mergeEvents(events, page.events)
            olderPages++
        }
        return events
    }
}
