package com.example.lolsito.feature.match.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.lolsito.core.ui.AppCard
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.ErrorView
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.InlineError
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.Shapes
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.rememberTeamAccent
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.match.domain.CalendarEvent
import com.example.lolsito.feature.match.domain.GameOutcome
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
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
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
    val gameOutcomes by viewModel.gameOutcomes.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    MatchDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onShowResult = viewModel::revealResult,
        onGameClick = onGameClick,
        onTeamClick = onTeamClick,
        history = history,
        gameOutcomes = gameOutcomes,
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
    /** Who won each completed game, by game id; see [MatchDetailViewModel.gameOutcomes]. */
    gameOutcomes: Map<String, GameOutcome> = emptyMap(),
    onRetryHistory: () -> Unit = {},
    onMatchClick: (String) -> Unit = {},
    /** When the match starts, if known: with it a series still to be played offers "Add to calendar". */
    startTime: Instant? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        AppTopBar("Match", onBack)
        when (state) {
            MatchDetailUiState.Loading -> MatchDetailSkeleton()
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
                    // Hidden results hide who won each game too, whatever was worked out.
                    if (state.resultsHidden) emptyMap() else gameOutcomes,
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
    gameOutcomes: Map<String, GameOutcome>,
) {
    val detail = content.detail
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        content.liveLink?.let { live ->
            item(key = "watch-live") {
                Button(
                    onClick = { context.openWatchLink(live) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
                ) { Text("Watch live") }
            }
        }
        item(key = "header") { MatchHeader(content, onTeamClick) }
        if (calendarEvent != null) {
            item(key = "add-to-calendar") {
                OutlinedButton(
                    onClick = { context.addToCalendar(calendarEvent) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
                ) { Text("Add to calendar") }
            }
        }
        if (content.resultsHidden) {
            item(key = "show-result") {
                OutlinedButton(
                    onClick = onShowResult,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
                ) { Text("Show result") }
            }
        }
        if (content.showsGames) {
            item(key = "games-title") { SectionTitle("Games") }
            val games = content.displayedGames
            if (games.isEmpty()) {
                item(key = "no-games") { InfoText("No games yet") }
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
                    outcome = game.id?.let(gameOutcomes::get),
                )
            }
        }
        historySections(history, detail.teams, content.showsHeadToHead, onRetryHistory, onMatchClick)
    }
}

/**
 * "Recent form" and, with [showHeadToHead], "Head to head", below the games. Nothing for
 * [MatchHistoryUiState.None]; while loading each section shows skeleton rows under its title, and after a failure
 * an error with a retry. The form of both teams shares one card; each meeting has its own.
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
        item(key = "form") {
            AppCard(Modifier.fillMaxWidth().cardMargins()) {
                loaded.form.forEach { form -> TeamFormRow(form) }
            }
        }
    }
    if (!showHeadToHead) return
    item(key = "h2h-title") { SectionTitle("Head to head") }
    when {
        loaded == null -> item(key = "h2h-status") { HistoryStatus(history, onRetry) }
        loaded.meetings.isEmpty() -> item(key = "h2h-empty") { InfoText("No previous meetings") }
        else -> items(loaded.meetings, key = { "meeting:${it.matchId}" }) { meeting ->
            MeetingRow(meeting, teams, onClick = { onMatchClick(meeting.matchId) })
        }
    }
}

/** The margins of a card in this screen's list: inset from the screen edges and from its neighbours. */
private fun Modifier.cardMargins(): Modifier = padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2)

/** What a section shows while the history is loading (skeleton rows) or after it failed (an error with a retry). */
@Composable
private fun HistoryStatus(history: MatchHistoryUiState, onRetry: () -> Unit) {
    if (history == MatchHistoryUiState.Error) {
        InlineError("Could not load", onRetry)
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Screen, vertical = 8.dp)
                .semantics { contentDescription = "Loading" },
            verticalArrangement = Arrangement.spacedBy(Spacing.CardGap),
        ) {
            repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(32.dp)) }
        }
    }
}

/**
 * Loading state of the whole screen, shaped like it: the two team logos around the score, then a title and
 * three game cards.
 */
@Composable
private fun MatchDetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(Spacing.Screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                SkeletonBlock(Modifier.size(56.dp), CircleShape)
                Spacer(Modifier.width(32.dp))
                SkeletonBlock(Modifier.size(width = 72.dp, height = 32.dp))
                Spacer(Modifier.width(32.dp))
                SkeletonBlock(Modifier.size(56.dp), CircleShape)
            }
            SkeletonBlock(
                Modifier.padding(start = Spacing.Screen, top = 16.dp, bottom = 8.dp).size(width = 96.dp, height = 20.dp),
            )
            repeat(3) {
                AppCard(Modifier.fillMaxWidth().cardMargins()) {
                    SkeletonBlock(Modifier.size(width = 88.dp, height = 18.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.size(width = 140.dp, height = 12.dp))
                }
            }
        }
    }
}

/** One team's recent form: its logo and code, then a mark per series, most recent first. It has no side padding: it goes inside a card. */
@Composable
fun TeamFormRow(form: TeamForm, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogo(form.team.imageUrl, contentDescription = form.team.name, size = 28.dp)
        Text(
            form.team.code,
            style = LabelText.TeamCode,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).width(56.dp),
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

/**
 * A previous meeting as a card: the score between the codes of the current match's [teams], and its date and
 * league below. The team that lost it is dimmed.
 */
@Composable
fun MeetingRow(meeting: Meeting, teams: List<Team>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .cardMargins()
            .clip(Shapes.Card)
            .clickable(onClickLabel = "View match", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                MeetingTeamCode(teams.getOrNull(0), meeting.winnerIndex, index = 0)
            }
            Text(
                meeting.score ?: "vs",
                style = LabelText.Score,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                MeetingTeamCode(teams.getOrNull(1), meeting.winnerIndex, index = 1)
            }
        }
        val caption = listOfNotNull(
            meetingDateFormatter.format(meeting.date),
            meeting.leagueName.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The code of the team at [index]: bold when it won the meeting, dimmed when it lost; neither while [winnerIndex] is unknown. */
@Composable
private fun MeetingTeamCode(team: Team?, winnerIndex: Int?, index: Int) {
    Text(
        team?.code.orEmpty(),
        style = LabelText.TeamCode,
        fontWeight = if (winnerIndex == index) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.alpha(if (winnerIndex != null && winnerIndex != index) LOSER_ALPHA else 1f),
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
    val firstAccent = rememberTeamAccent(detail.teams.getOrNull(0)?.imageUrl)
    val secondAccent = rememberTeamAccent(detail.teams.getOrNull(1)?.imageUrl)
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier
            .fillMaxWidth()
            // Each team's color glows from its side and fades out towards the middle and the bottom.
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        0f to firstAccent.copy(alpha = firstAccent.alpha * ACCENT_GLOW_ALPHA),
                        0.5f to Color.Transparent,
                        1f to secondAccent.copy(alpha = secondAccent.alpha * ACCENT_GLOW_ALPHA),
                    ),
                )
                drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to background))
            }
            .padding(16.dp),
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

/** How strong a team's color is at the edge of the match header. */
private const val ACCENT_GLOW_ALPHA = 0.38f

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
            fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** The team that won a game, by logo and code, and under it the series score after that game when it is known. */
@Composable
private fun GameWinner(winner: Team, seriesScore: String?) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Won by ${winner.code}" + (seriesScore?.let { ", series at $it" } ?: "")
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(winner.imageUrl, contentDescription = null, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(winner.code, style = LabelText.TeamCode, fontWeight = FontWeight.Bold, color = WinGreen)
        }
        if (seriesScore != null) {
            Text(seriesScore, style = LabelText.Number, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One game of the series as a card: its name and each team's side, its state, a Watch button when it has a VOD
 * ([onWatch]) and a chevron when it opens its stats ([onClick]).
 */
@Composable
fun GameRow(
    game: MatchGame,
    teams: List<Team>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onWatch: (() -> Unit)? = null,
    /** False while results are hidden: the game's state would give them away. */
    showState: Boolean = true,
    /** Who won the game and the series score after it, when known; it takes the place of "Completed". */
    outcome: GameOutcome? = null,
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .cardMargins()
            .clip(Shapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "View stats", onClick = onClick) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Game ${game.number}", style = MaterialTheme.typography.titleMedium)
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
            val winner = outcome?.let { teams.getOrNull(it.winnerIndex) }
            if (showState && winner != null) {
                GameWinner(winner, outcome.seriesScore)
            } else if (showState) {
                if (game.state == GameState.IN_PROGRESS) {
                    LiveBadge()
                } else {
                    Text(
                        game.state.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        // Not drawn as text: a live game shows the LiveBadge instead.
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

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchDetailContentPreview() {
    LolSitoTheme {
        MatchDetailContent(
            state = MatchDetailUiState.Content(previewDetail),
            onBack = {},
            onRetry = {},
            onGameClick = {},
            history = MatchHistoryUiState.Content(previewHistory),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchDetailHistoryErrorPreview() {
    LolSitoTheme {
        MatchDetailContent(
            state = MatchDetailUiState.Content(previewDetail),
            onBack = {},
            onRetry = {},
            onGameClick = {},
            history = MatchHistoryUiState.Error,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchDetailErrorPreview() {
    LolSitoTheme {
        MatchDetailContent(
            state = MatchDetailUiState.Error("Could not load the match"),
            onBack = {},
            onRetry = {},
            onGameClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchDetailHiddenPreview() {
    LolSitoTheme {
        MatchDetailContent(
            state = MatchDetailUiState.Content(previewDetail, resultsHidden = true),
            onBack = {},
            onRetry = {},
            onGameClick = {},
        )
    }
}
