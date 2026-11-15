package com.example.lolsito.feature.match.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.ui.ErrorView
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LoadingView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.match.domain.CalendarEvent
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.GameStatsLink
import com.example.lolsito.feature.match.domain.FormEntry
import com.example.lolsito.feature.match.domain.FormOutcome
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchHistory
import com.example.lolsito.feature.match.domain.Meeting
import com.example.lolsito.feature.match.domain.TeamForm
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.example.lolsito.feature.match.domain.MatchGame
import com.example.lolsito.feature.match.domain.WatchLink
import com.example.lolsito.ui.theme.WinGreen

/** Container: wires the ViewModel to the stateless [MatchDetailContent]. */
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    onBack: () -> Unit,
    onGameClick: (GameStatsLink) -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val startTime by viewModel.startTime.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    MatchDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onShowResult = viewModel::revealResult,
        onGameClick = onGameClick,
        onTeamClick = onTeamClick,
        history = history,
        onRetryHistory = viewModel::retryHistory,
        onMatchClick = onMatchClick,
        modifier = modifier,
        startTime = startTime,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailContent(
    state: MatchDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGameClick: (GameStatsLink) -> Unit,
    modifier: Modifier = Modifier,
    onShowResult: () -> Unit = {},
    onTeamClick: (String) -> Unit = {},
    history: MatchHistoryUiState = MatchHistoryUiState.None,
    onRetryHistory: () -> Unit = {},
    onMatchClick: (String) -> Unit = {},
    /** When the match starts, if known: with it a series still to be played offers "Add to calendar". */
    startTime: Instant? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Match") },
            // The app Scaffold already applies the system bar insets.
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
            },
        )
        when (state) {
            MatchDetailUiState.Loading -> LoadingView()
            is MatchDetailUiState.Error -> ErrorView(state.message, onRetry)
            is MatchDetailUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                MatchDetailBody(
                    state,
                    // Hidden results hide the history too, whatever state it is in.
                    if (state.resultsHidden) MatchHistoryUiState.None else history,
                    state.calendarEvent(startTime),
                    onShowResult,
                    onGameClick,
                    onTeamClick,
                    onRetryHistory,
                    onMatchClick,
                )
            }
        }
    }
}

@Composable
private fun MatchDetailBody(
    content: MatchDetailUiState.Content,
    history: MatchHistoryUiState,
    calendarEvent: CalendarEvent?,
    onShowResult: () -> Unit,
    onGameClick: (GameStatsLink) -> Unit,
    onTeamClick: (String) -> Unit,
    onRetryHistory: () -> Unit,
    onMatchClick: (String) -> Unit,
) {
    val detail = content.detail
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        content.liveLink?.let { live ->
            item(key = "watch-live") {
                Button(
                    onClick = { context.openWatchLink(live) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Watch live") }
            }
        }
        item(key = "header") { MatchHeader(content, onTeamClick) }
        if (calendarEvent != null) {
            item(key = "add-to-calendar") {
                OutlinedButton(
                    onClick = { context.addToCalendar(calendarEvent) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Add to calendar") }
            }
        }
        if (content.resultsHidden) {
            item(key = "show-result") {
                OutlinedButton(
                    onClick = onShowResult,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Show result") }
            }
        }
        if (content.showsGames) {
            item(key = "games-title") {
                Text(
                    "Games",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val games = content.displayedGames
            if (games.isEmpty()) {
                item(key = "no-games") {
                    Text(
                        "No games yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val keys = content.displayedGameKeys
            items(games.size, key = { keys[it] }) { index ->
                val game = games[index]
                val statsLink = content.statsLinkFor(game)
                GameRow(
                    game,
                    detail.teams,
                    onClick = statsLink?.let { link -> { onGameClick(link) } },
                    onWatch = content.watchLinkFor(game)?.let { vod -> { context.openWatchLink(vod) } },
                    showState = content.showsGameState,
                )
                HorizontalDivider()
            }
        }
        historySections(history, detail.teams, content.showsHeadToHead, onRetryHistory, onMatchClick)
    }
}

/**
 * "Recent form" and, with [showHeadToHead], "Head to head", below the games. Nothing for
 * [MatchHistoryUiState.None]; while loading or after a failure each section says so under its title, the failure
 * with a retry.
 */
private fun LazyListScope.historySections(
    history: MatchHistoryUiState,
    teams: List<Team>,
    showHeadToHead: Boolean,
    onRetry: () -> Unit,
    onMatchClick: (String) -> Unit,
) {
    if (history == MatchHistoryUiState.None) return
    val loaded = (history as? MatchHistoryUiState.Content)?.history
    item(key = "form-title") { SectionTitle("Recent form") }
    if (loaded == null) {
        item(key = "form-status") { HistoryStatus(history, onRetry) }
    } else {
        items(loaded.form.size, key = { "form:$it" }) { index -> TeamFormRow(loaded.form[index]) }
    }
    if (!showHeadToHead) return
    item(key = "h2h-title") { SectionTitle("Head to head") }
    when {
        loaded == null -> item(key = "h2h-status") { HistoryStatus(history, onRetry) }
        loaded.meetings.isEmpty() -> item(key = "h2h-empty") { HistoryNote("No previous meetings") }
        else -> items(loaded.meetings, key = { "meeting:${it.matchId}" }) { meeting ->
            MeetingRow(meeting, teams, onClick = { onMatchClick(meeting.matchId) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun HistoryNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/** What a section shows while the history is loading or after it failed. */
@Composable
private fun HistoryStatus(history: MatchHistoryUiState, onRetry: () -> Unit) {
    if (history == MatchHistoryUiState.Error) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HistoryNote("Could not load", Modifier.weight(1f))
            TextButton(onClick = onRetry, modifier = Modifier.padding(end = 8.dp)) { Text("Retry") }
        }
    } else {
        HistoryNote("Loading…")
    }
}

/** One team's recent form: its logo and code, then a mark per series, most recent first. */
@Composable
fun TeamFormRow(form: TeamForm, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(form.team.imageUrl, contentDescription = form.team.name, size = 28.dp)
        Text(
            form.team.code,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp).width(48.dp),
        )
        if (form.entries.isEmpty()) {
            Text(
                "No recent matches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.entries.forEach { entry -> FormMark(entry) }
            }
        }
    }
}

/** "W" or "L" over the opponent's code. */
@Composable
private fun FormMark(entry: FormEntry) {
    val won = entry.outcome == FormOutcome.WIN
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = (if (won) "Win against " else "Loss against ") + entry.opponentCode
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (won) "W" else "L",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (won) WinGreen else MaterialTheme.colorScheme.error,
        )
        Text(
            entry.opponentCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A previous meeting: its date and league, and the score between the codes of the current match's [teams]. */
@Composable
fun MeetingRow(meeting: Meeting, teams: List<Team>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View match", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(meetingDateFormatter.format(meeting.date), style = MaterialTheme.typography.bodyLarge)
            if (meeting.leagueName.isNotBlank()) {
                Text(
                    meeting.leagueName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MeetingTeamCode(teams.getOrNull(0), meeting.winnerIndex == 0)
        Text(
            meeting.score ?: "vs",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        MeetingTeamCode(teams.getOrNull(1), meeting.winnerIndex == 1)
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun MeetingTeamCode(team: Team?, won: Boolean) {
    Text(
        team?.code.orEmpty(),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (won) FontWeight.ExtraBold else FontWeight.Normal,
    )
}

private val meetingDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun MatchHeader(
    content: MatchDetailUiState.Content,
    onTeamClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detail = content.detail
    val result = content.displayedResult
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val meta = listOfNotNull(
            detail.leagueName.takeIf { it.isNotBlank() },
            detail.blockName,
            result.formatLabel,
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val teams = detail.teams
        if (teams.size >= 2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderTeam(teams[0], result.emphasisFor(0), onTeamClick, Modifier.weight(1f))
                Text(
                    text = content.headerLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (content.headerIsLive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HeaderTeam(teams[1], result.emphasisFor(1), onTeamClick, Modifier.weight(1f))
            }
        } else {
            Text("TBD", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HeaderTeam(
    team: Team,
    emphasis: TeamEmphasis,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileId = team.profileId()
    Column(
        modifier
            .alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f)
            .then(
                if (profileId != null) {
                    Modifier.clickable(onClickLabel = "View team", onClick = { onTeamClick(profileId) })
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TeamLogo(team.imageUrl, contentDescription = team.name, size = 56.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            team.code,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}

@Composable
fun GameRow(
    game: MatchGame,
    teams: List<Team>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onWatch: (() -> Unit)? = null,
    /** False while results are hidden: the game's state would give them away. */
    showState: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "View stats", onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Game ${game.number}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val sides = teams.mapNotNull { team ->
                game.sideOf(team.id)?.let { side -> "${team.code} ${side.label}" }
            }
            if (sides.isNotEmpty()) {
                Text(
                    sides.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (showState) {
            Text(
                game.state.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (game.state == GameState.IN_PROGRESS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onWatch != null) {
            OutlinedButton(onClick = onWatch, modifier = Modifier.padding(start = 8.dp)) { Text("Watch") }
        }
        if (onClick != null) {
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Opens [link] in the provider app or the browser; does nothing when no app can handle it. */
private fun Context.openWatchLink(link: WatchLink) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open it: ignore.
    }
}

/**
 * Opens the calendar app's "new event" screen prefilled with [event]; the user saves it there, so no calendar
 * permission is needed. Does nothing when no app can handle it.
 */
private fun Context.addToCalendar(event: CalendarEvent) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No calendar app: ignore.
    }
}

private val GameSide.label: String
    get() = when (this) {
        GameSide.BLUE -> "blue"
        GameSide.RED -> "red"
    }

private val GameState.label: String
    get() = when (this) {
        GameState.UNSTARTED -> "Upcoming"
        GameState.IN_PROGRESS -> "LIVE"
        GameState.COMPLETED -> "Completed"
        GameState.UNNEEDED -> "Not needed"
        GameState.UNKNOWN -> "Unknown"
    }

private val previewDetail = MatchDetail(
    matchId = "1",
    state = MatchState.COMPLETED,
    leagueName = "Worlds",
    leagueSlug = "worlds",
    leagueImageUrl = null,
    blockName = "Quarterfinals",
    teams = listOf(
        Team("MVK Esports", "MVK", null, null, 3, id = "a"),
        Team("FURIA", "FUR", null, null, 0, id = "b"),
    ),
    format = SeriesFormat(SeriesType.BEST_OF, 5),
    games = (1..5).map { n ->
        MatchGame(
            number = n,
            id = "g$n",
            state = if (n <= 3) GameState.COMPLETED else GameState.UNNEEDED,
            sides = mapOf("a" to GameSide.BLUE, "b" to GameSide.RED),
        )
    },
)

private val previewHistory = MatchHistory(
    form = listOf(
        TeamForm(
            previewDetail.teams[0],
            listOf(
                FormEntry("f1", FormOutcome.WIN, "AAA"),
                FormEntry("f2", FormOutcome.LOSS, "BBB"),
                FormEntry("f3", FormOutcome.WIN, "CCC"),
            ),
        ),
        TeamForm(previewDetail.teams[1], emptyList()),
    ),
    meetings = listOf(Meeting("h1", LocalDate.of(2026, 1, 15), "Sample League", score = "2 - 1", winnerIndex = 0)),
)

@Preview(showBackground = true)
@Composable
private fun MatchDetailContentPreview() {
    MatchDetailContent(
        state = MatchDetailUiState.Content(previewDetail),
        onBack = {},
        onRetry = {},
        onGameClick = {},
        history = MatchHistoryUiState.Content(previewHistory),
    )
}

@Preview(showBackground = true)
@Composable
private fun MatchDetailHistoryErrorPreview() {
    MatchDetailContent(
        state = MatchDetailUiState.Content(previewDetail),
        onBack = {},
        onRetry = {},
        onGameClick = {},
        history = MatchHistoryUiState.Error,
    )
}

@Preview(showBackground = true)
@Composable
private fun MatchDetailErrorPreview() {
    MatchDetailContent(
        state = MatchDetailUiState.Error("Could not load the match"),
        onBack = {},
        onRetry = {},
        onGameClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun MatchDetailHiddenPreview() {
    MatchDetailContent(
        state = MatchDetailUiState.Content(previewDetail, resultsHidden = true),
        onBack = {},
        onRetry = {},
        onGameClick = {},
    )
}
