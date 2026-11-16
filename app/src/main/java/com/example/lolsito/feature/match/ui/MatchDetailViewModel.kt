package com.example.lolsito.feature.match.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.polling.MATCH_DETAIL_POLL_INTERVAL
import com.example.lolsito.core.polling.pollWhile
import com.example.lolsito.core.preferences.UserPreferences
import com.example.lolsito.feature.match.domain.GameOutcome
import com.example.lolsito.feature.match.domain.GameWinnerSource
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.completedGameIds
import com.example.lolsito.feature.match.domain.gameOutcomes
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
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 *
 * Event details carry no start time, so [startTime] comes from those same schedule events: the one of this match,
 * when it is among them. It is not a result, so it is exposed whether or not results are hidden.
 *
 * [refresh] (pull to refresh) loads the series and then the history again while both stay on screen; whatever
 * fails stays as it was. The polling is paused for as long as it runs, so the two never overlap.
 */
class MatchDetailViewModel(
    private val matchId: String,
    private val repository: MatchDetailRepository,
    private val preferences: UserPreferences,
    private val teamsRepository: TeamsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val leaguesRepository: LeaguesRepository,
    private val zoneId: ZoneId,
    private val gameWinners: GameWinnerSource = GameWinnerSource { null },
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    private val _historyState = MutableStateFlow<MatchHistoryUiState>(MatchHistoryUiState.None)
    val historyState: StateFlow<MatchHistoryUiState> = _historyState.asStateFlow()

    private val _startTime = MutableStateFlow<Instant?>(null)

    /** When this match starts, once the history events were read and only if it is among them. */
    val startTime: StateFlow<Instant?> = _startTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /** A pull to refresh is running; the content stays as it is meanwhile. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _gameOutcomes = MutableStateFlow<Map<String, GameOutcome>>(emptyMap())

    /**
     * Who won each completed game, by game id, and the series score after it. The API does not say, so
     * [gameWinners] is asked once per completed game and the series score settles what it cannot tell
     * (see [gameOutcomes]); a game whose winner stays unknown has no entry. Secondary like the history: it
     * loads on its own and a failure only leaves games without a winner. Empty while results are hidden.
     */
    val gameOutcomes: StateFlow<Map<String, GameOutcome>> = _gameOutcomes.asStateFlow()

    /** What was worked out, whether or not it may be shown; [gameOutcomes] is what the screen gets. */
    private var outcomes: Map<String, GameOutcome> = emptyMap()

    /** The completed games [outcomes] was worked out for; null to work it out again. */
    private var outcomesFor: List<String>? = null
    private var outcomesJob: Job? = null

    /** What was loaded, whether or not it may be shown; [historyState] is what the screen gets. */
    private var history: MatchHistoryUiState = MatchHistoryUiState.None

    private var loadJob: Job? = null
    private var historyJob: Job? = null
    private var refreshJob: Job? = null
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
        publishOutcomes()
    }

    private fun resultsHidden(): Boolean = noSpoilers == true && !revealed

    /**
     * Keeps the content fresh for as long as it is in progress. The screen runs it only while visible,
     * so leaving the screen cancels it. A refresh that fails leaves the content as it was; an initial
     * load that is still running, failed or completed is never polled, and [retry] pauses polling
     * until its own load ends. So does [refresh]: it stops a tick in flight, and the next one comes a
     * full interval after it ends.
     */
    suspend fun pollWhileVisible() {
        val polled = combine(uiState, isRefreshing) { state, refreshing ->
            !refreshing && (state as? MatchDetailUiState.Content)?.detail?.state == MatchState.IN_PROGRESS
        }
        pollWhile(MATCH_DETAIL_POLL_INTERVAL, polled) { publishFreshDetail() }
    }

    /**
     * Pull to refresh: loads the series again, then its history, without clearing either. Only while the series
     * is shown and no refresh is already running. A failure leaves what failed as it was.
     */
    fun refresh() {
        if (_uiState.value !is MatchDetailUiState.Content || refreshJob?.isActive == true) return
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            try {
                outcomesFor = null // Ask again for the winners that could not be told or failed.
                val detail = publishFreshDetail()
                refreshHistory(detail.teams)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The content stays as it was.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun publishFreshDetail(): MatchDetail {
        val detail = repository.getMatchDetail(matchId)
        // Only replace content: a reload started meanwhile owns the state.
        _uiState.update { if (it is MatchDetailUiState.Content) MatchDetailUiState.Content(detail, resultsHidden()) else it }
        loadGameOutcomes(detail)
        return detail
    }

    /**
     * Works out who won each completed game of [detail], unless that was done for these same games (the
     * polling of a live series only asks again when another game has ended). Every game is asked at once.
     */
    private fun loadGameOutcomes(detail: MatchDetail) {
        val gameIds = detail.completedGameIds()
        if (gameIds == outcomesFor) return
        outcomesFor = gameIds
        outcomesJob?.cancel()
        if (gameIds.isEmpty()) {
            outcomes = emptyMap()
            publishOutcomes()
            return
        }
        outcomesJob = viewModelScope.launch {
            val winnerTeamIds = coroutineScope {
                gameIds.map { gameId -> async { gameId to winnerOf(gameId) } }.awaitAll().toMap()
            }
            outcomes = detail.gameOutcomes(winnerTeamIds)
            publishOutcomes()
        }
    }

    /** A request that fails is a winner the stats could not tell. */
    private suspend fun winnerOf(gameId: String): String? = try {
        gameWinners.winnerTeamId(gameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun publishOutcomes() {
        _gameOutcomes.value = if (resultsHidden()) emptyMap() else outcomes
    }

    /**
     * A loaded history is replaced in place, and kept when the reload fails; one that failed is loaded again as
     * [retryHistory] does. One still loading is left to finish.
     */
    private suspend fun refreshHistory(teams: List<Team>) {
        when (history) {
            MatchHistoryUiState.Error -> loadHistory(teams)
            is MatchHistoryUiState.Content -> {
                if (teams.size != 2) return
                history = MatchHistoryUiState.Content(readHistory(teams))
                publishHistory()
            }
            else -> Unit
        }
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
        refreshJob?.cancel()
        _uiState.value = MatchDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val hidden = readNoSpoilers()
                val detail = repository.getMatchDetail(matchId)
                if (historyJob == null) loadHistory(detail.teams)
                loadGameOutcomes(detail)
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
                MatchHistoryUiState.Content(readHistory(teams))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MatchHistoryUiState.Error
            }
            publishHistory()
        }
    }

    /** Fetches the history events and, from the same events, this match's start time. */
    private suspend fun readHistory(teams: List<Team>): MatchHistory {
        val events = fetchHistoryEvents(teams)
        _startTime.value = events.firstOrNull { it.matchId == matchId }?.startTime
        return buildMatchHistory(events, teams, matchId, zoneId)
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
