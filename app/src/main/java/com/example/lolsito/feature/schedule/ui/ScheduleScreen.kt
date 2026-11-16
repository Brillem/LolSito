package com.example.lolsito.feature.schedule.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.ui.ChevronDownIcon
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.MatchCard
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.MessageView
import com.example.lolsito.core.ui.PickerRow
import com.example.lolsito.core.ui.PickerSheet
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.isFavoriteIn
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Container: wires the ViewModel to the stateless [ScheduleContent]. */
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    // Ticks once per minute, on the minute, only while the screen is visible; drives the match countdowns.
    var now by remember { mutableStateOf(Instant.now()) }
    WhileStarted(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L - now.toEpochMilli() % 60_000L)
        }
    }
    // Favorites are starred in a team's profile: read them again whenever this tab comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshFavorites() }
    CompositionLocalProvider(LocalNow provides now) {
    ScheduleContent(
        state = state,
        onToggleLeague = viewModel::toggleLeague,
        onToggleLiveOnly = viewModel::toggleLiveOnly,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onToggleNoSpoilers = viewModel::toggleNoSpoilers,
        onRetry = viewModel::retry,
        onLoadOlder = viewModel::loadOlder,
        onLoadNewer = viewModel::loadNewer,
        onMatchClick = onMatchClick,
        modifier = modifier,
        onRefresh = viewModel::refresh,
    )
    }
}

/** The current minute, ticked by [ScheduleScreen]; null (no countdowns) wherever nothing provides it. */
private val LocalNow = compositionLocalOf<Instant?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    state: ScheduleUiState,
    onToggleLeague: (String) -> Unit,
    onToggleLiveOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleNoSpoilers: () -> Unit,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onLoadNewer: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Pull to refresh on the lists; the loading, error and empty states do not scroll, so they do not offer it. */
    onRefresh: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScheduleFilterRow(
                state.leagues,
                state.selectedLeagueIds,
                state.liveOnly,
                onToggleLeague,
                onToggleLiveOnly,
                Modifier.weight(1f),
                favoritesOnly = state.favoritesOnly,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
            )
            NoSpoilersToggle(state.noSpoilers, onToggleNoSpoilers)
        }
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (state.liveOnly) {
                // The toggle replaces the page, so the schedule's own loading, error and empty states do not apply.
                LiveOnlyList(state, onMatchClick)
            } else {
                LoadStatusView(
                    status = state.status,
                    emptyMessage = if (state.selectedLeagueIds.isEmpty()) "Select a league to see its matches"
                    else "No matches scheduled",
                    onRetry = onRetry,
                ) {
                    ScheduleList(state, onLoadOlder, onLoadNewer, onMatchClick)
                }
            }
        }
    }
}

/** Eye at the top: open shows results, crossed out means no-spoilers mode is on and finished results are hidden. */
@Composable
private fun NoSpoilersToggle(noSpoilers: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            contentDescription = if (noSpoilers) "Show results" else "Hide results"
            stateDescription = if (noSpoilers) "No spoilers on" else "No spoilers off"
        },
    ) {
        Icon(
            imageVector = if (noSpoilers) EyeOffIcon else EyeIcon,
            contentDescription = null,
            tint = if (noSpoilers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The LIVE toggle, always first and always there, then the Favorites toggle, then once the leagues
 * loaded a single chip that shows which are on and opens a sheet to toggle them. Without
 * [onToggleFavoritesOnly] (callers that have no favorites) the Favorites chip is left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFilterRow(
    leagues: List<League>,
    selectedIds: Set<String>,
    liveOnly: Boolean,
    onToggleLeague: (String) -> Unit,
    onToggleLiveOnly: () -> Unit,
    modifier: Modifier = Modifier,
    favoritesOnly: Boolean = false,
    onToggleFavoritesOnly: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    var sheetOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = liveOnly,
            onClick = onToggleLiveOnly,
            label = { Text("LIVE", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
            leadingIcon = {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (liveOnly) MaterialTheme.colorScheme.onPrimary else accent),
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = MaterialTheme.colorScheme.inversePrimary,
                selectedContainerColor = accent,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = liveOnly,
                borderColor = accent,
                selectedBorderColor = accent,
            ),
        )
        if (onToggleFavoritesOnly != null) {
            FilterChip(
                selected = favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = { Text("Favorites", maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(
                        imageVector = StarIcon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = favoritesOnly,
                    selectedBorderColor = accent,
                ),
            )
        }
        if (leagues.isNotEmpty()) {
            val selectedLeagues = leagues.filter { it.id in selectedIds }
            val logos = selectedLeagues.mapNotNull { it.imageUrl }.take(MAX_CHIP_LOGOS)
            FilterChip(
                selected = selectedLeagues.isNotEmpty(),
                onClick = { sheetOpen = true },
                label = {
                    Text(
                        leaguesChipLabel(selectedLeagues.map { it.name }),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // Logos are not tinted: they are often white on transparent, which reads on the black chip.
                leadingIcon = if (logos.isEmpty()) null else {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            logos.forEach { TeamLogo(it, contentDescription = null, size = 18.dp) }
                        }
                    }
                },
                trailingIcon = {
                    Icon(ChevronDownIcon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedLeagues.isNotEmpty(),
                    selectedBorderColor = accent,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
    if (sheetOpen && leagues.isNotEmpty()) {
        // Multi-select: the sheet stays open while leagues are toggled.
        PickerSheet(title = "Leagues", onDismiss = { sheetOpen = false }, sheetState = rememberModalBottomSheetState()) {
            LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                items(leagues, key = { it.id }) { league ->
                    PickerRow(
                        label = league.name,
                        imageUrl = league.imageUrl,
                        selected = league.id in selectedIds,
                        onClick = { onToggleLeague(league.id) },
                    )
                }
            }
        }
    }
}

private const val MAX_CHIP_LOGOS = 3

/**
 * Label of the leagues chip: the name when one league is on, "Leagues" when none and only the count when
 * several, since the chip already shows their logos and has little room next to the other filters.
 */
internal fun leaguesChipLabel(selectedNames: List<String>): String = when (selectedNames.size) {
    0 -> "Leagues"
    1 -> selectedNames.first()
    else -> selectedNames.size.toString()
}

/** Live matches of every league; an empty feed shows a message instead of an empty list. */
@Composable
private fun LiveOnlyList(state: ScheduleUiState, onMatchClick: (String) -> Unit) {
    if (state.liveEvents.isEmpty()) {
        MessageView(state.favoritesEmptyMessage ?: "No live matches right now")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        liveRows(state, onMatchClick)
    }
}

private fun LazyListScope.liveRows(state: ScheduleUiState, onMatchClick: (String) -> Unit) {
    itemsIndexed(state.liveEvents, key = { index, _ -> state.liveKeys[index] }) { _, event ->
        EventRow(event, state.zoneId, isLive = true, noSpoilers = state.noSpoilers, favoriteNames = state.favoriteNames, onClick = event.matchId?.let { id -> { onMatchClick(id) } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleList(
    state: ScheduleUiState,
    onLoadOlder: () -> Unit,
    onLoadNewer: () -> Unit,
    onMatchClick: (String) -> Unit,
) {
    // A section is shown while it has days or a paging control, which needs its header to make sense.
    val showUpcoming = state.upcomingDays.isNotEmpty() || state.canLoadNewer
    val showResults = state.resultDays.isNotEmpty() || state.canLoadOlder
    LazyColumn(Modifier.fillMaxSize()) {
        state.favoritesEmptyMessage?.let { message ->
            item(key = "favorites-empty") {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        if (state.liveEvents.isNotEmpty()) {
            item(key = "live-header") { SectionHeader("Live now", accent = true) }
            liveRows(state, onMatchClick)
        }
        if (showUpcoming) {
            item(key = "upcoming-header") { SectionHeader("Upcoming") }
            dayRows(state.upcomingDays, state.zoneId, state.noSpoilers, state.favoriteNames, onMatchClick)
            item(key = "newer") {
                PagingButton(
                    "Load newer",
                    available = state.canLoadNewer,
                    enabled = !state.isPaging,
                    loading = state.isLoadingNewer,
                    onClick = onLoadNewer,
                )
                state.newerError?.let { PagingError(it) }
            }
        }
        if (showResults) {
            item(key = "results-header") { SectionHeader("Results") }
            dayRows(state.resultDays, state.zoneId, state.noSpoilers, state.favoriteNames, onMatchClick)
            item(key = "older") {
                PagingButton(
                    "Load older",
                    available = state.canLoadOlder,
                    enabled = !state.isPaging,
                    loading = state.isLoadingOlder,
                    onClick = onLoadOlder,
                )
                state.olderError?.let { PagingError(it) }
            }
        }
        if (state.liveEvents.isEmpty() && !showUpcoming && !showResults) {
            item(key = "empty-range") {
                Text(
                    "No matches in this range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.dayRows(
    days: List<ScheduleDay>,
    zoneId: ZoneId,
    noSpoilers: Boolean,
    favoriteNames: List<String>,
    onMatchClick: (String) -> Unit,
) {
    days.forEach { day ->
        stickyHeader(key = day.headerKey) { DayHeader(day.date) }
        itemsIndexed(day.events, key = { index, _ -> day.keys[index] }) { _, event ->
            // Live matches have their own section, so a row here is never live, whatever its state says.
            EventRow(event, zoneId, isLive = false, noSpoilers = noSpoilers, favoriteNames = favoriteNames, onClick = event.matchId?.let { id -> { onMatchClick(id) } })
        }
    }
}

/** Hidden when there is no cursor; disabled while any page loads, since only one loads at a time. */
@Composable
private fun PagingButton(label: String, available: Boolean, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    if (!available && !loading) return
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
            ) { Text(label) }
        }
    }
}

@Composable
private fun PagingError(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun DayHeader(date: LocalDate, modifier: Modifier = Modifier) {
    // Opaque, since as a sticky header the cards scroll under it.
    Text(
        text = date.format(dayFormatter).uppercase(),
        style = LabelText.Overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = Spacing.Screen, end = Spacing.Screen, top = 12.dp, bottom = 4.dp),
    )
}

/** Title of one of the three sections; the red one marks what is happening now. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = if (accent) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Screen, end = Spacing.Screen, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Scoreboard card: first team, result, second team, with the caption centered below. The result is the
 * score of a finished match, the current score and the LIVE badge of a live one, and the start time of
 * a match that has not started. The start time of a finished match goes into the caption instead; a
 * live match shows no time at all, so the time appears once, wherever it matters.
 */
@Composable
fun EventRow(
    event: ScheduleEvent,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    /** Shows the LIVE badge; defaults to the event's own state for callers that have no live feed. */
    isLive: Boolean = event.state == MatchState.IN_PROGRESS,
    /** Hides a finished match's score and winner (see [eventRowDisplay]). */
    noSpoilers: Boolean = false,
    /** Names of the starred teams: a team with one of them gets a small star. Empty draws no star. */
    favoriteNames: List<String> = emptyList(),
    /** Null for rows without a match, which are not clickable. */
    onClick: (() -> Unit)? = null,
) {
    val teams = event.match?.teams.orEmpty()
    val hasTeams = teams.size >= 2
    val display = eventRowDisplay(event, isLive, noSpoilers)
    val result = display.result
    val time = event.startTime.atZone(zoneId).format(timeFormatter)
    val score = display.score
    // With a score (or live badge) in the middle the time has no place there: a finished match keeps it in the caption.
    val timeInCaption = !isLive && (score != null || !hasTeams)
    val now = LocalNow.current
    val countdown = now?.let { countdownLabel(event.startTime, it, event.state) }?.takeIf { !isLive && hasTeams }
    val caption = listOfNotNull(
        time.takeIf { timeInCaption },
        event.leagueName.takeIf { it.isNotBlank() },
        event.blockName,
        result.formatLabel,
    ).joinToString(" · ")
    MatchCard(modifier, onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (hasTeams) TeamCell(teams[0], result.emphasisFor(0), favorite = teams[0].isFavoriteIn(favoriteNames))
            }
            Column(
                Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    !hasTeams -> Text("TBD", style = LabelText.Score)
                    score != null -> Text(score, style = LabelText.Score)
                    else -> Text(time, style = LabelText.Time)
                }
                if (isLive) {
                    Spacer(Modifier.height(2.dp))
                    LiveBadge()
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (hasTeams) TeamCell(teams[1], result.emphasisFor(1), mirrored = true, favorite = teams[1].isFavoriteIn(favoriteNames))
            }
        }
        if (countdown != null) {
            val accent = MaterialTheme.colorScheme.primary
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = accent)) { append(countdown) }
                    if (caption.isNotEmpty()) append(" · $caption")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (caption.isNotEmpty()) {
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
}

/**
 * Logo and code; [mirrored] puts the code first, so the two teams of a row mirror each other around the
 * result. A [favorite] team gets a small star on the outer side, away from the result.
 */
@Composable
internal fun TeamCell(team: Team, emphasis: TeamEmphasis = TeamEmphasis.NEUTRAL, mirrored: Boolean = false, favorite: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f),
    ) {
        val logo = @Composable { TeamLogo(team.imageUrl, contentDescription = team.name) }
        val code = @Composable {
            Text(
                team.code,
                style = LabelText.TeamCode,
                fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        val star = @Composable {
            if (favorite) {
                Icon(
                    imageVector = StarIcon,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (mirrored) {
            code()
            Spacer(Modifier.width(6.dp))
            logo()
            if (favorite) Spacer(Modifier.width(4.dp))
            star()
        } else {
            star()
            if (favorite) Spacer(Modifier.width(4.dp))
            logo()
            Spacer(Modifier.width(6.dp))
            code()
        }
    }
}

private val previewTeams = listOf(Team("T1", "T1", null, null, null), Team("Gen.G", "GEN", null, null, null))

private val previewEvent = ScheduleEvent(
    startTime = Instant.parse("2026-10-20T07:00:00Z"),
    state = MatchState.UNSTARTED,
    blockName = "Swiss",
    leagueName = "Worlds",
    leagueSlug = "worlds",
    match = Match(id = "1", teams = previewTeams, format = SeriesFormat(SeriesType.BEST_OF, 3)),
)

private val previewLive = previewEvent.copy(
    state = MatchState.IN_PROGRESS,
    match = previewEvent.match!!.copy(
        id = "2",
        teams = listOf(Team("T1", "T1", null, null, 1), Team("Gen.G", "GEN", null, null, 0)),
    ),
)

private val previewCompleted = previewEvent.copy(
    state = MatchState.COMPLETED,
    match = previewEvent.match!!.copy(
        id = "3",
        teams = listOf(
            Team("T1", "T1", null, MatchOutcome.LOSS, 1),
            Team("Gen.G", "GEN", null, MatchOutcome.WIN, 2),
        ),
    ),
)

private val previewLeagues = listOf(League("1", "worlds", "Worlds", null, null), League("2", "lck", "LCK", null, null))

private val previewState = ScheduleUiState(
    leagues = previewLeagues,
    selectedLeagueIds = setOf("1"),
    status = LoadStatus.Content,
    liveEvents = listOf(previewLive),
    upcomingDays = listOf(ScheduleDay(LocalDate.of(2026, 10, 21), listOf(previewEvent), "upcoming:")),
    resultDays = listOf(ScheduleDay(LocalDate.of(2026, 10, 19), listOf(previewCompleted), "results:")),
    canLoadOlder = true,
    canLoadNewer = true,
    zoneId = ZoneId.of("UTC"),
)

@Composable
private fun PreviewContent(state: ScheduleUiState) {
    LolSitoTheme {
        ScheduleContent(
            state = state,
            onToggleLeague = {}, onToggleLiveOnly = {}, onToggleFavoritesOnly = {}, onToggleNoSpoilers = {}, onRetry = {}, onLoadOlder = {}, onLoadNewer = {}, onMatchClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScheduleContentPreview() = PreviewContent(previewState)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScheduleLiveToggleOnPreview() = PreviewContent(previewState.copy(liveOnly = true))

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScheduleLiveToggleEmptyPreview() = PreviewContent(previewState.copy(liveOnly = true, liveEvents = emptyList()))

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun UpcomingEventRowPreview() {
    LolSitoTheme { EventRow(previewEvent, ZoneId.of("UTC"), isLive = false, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LiveEventRowPreview() {
    LolSitoTheme { EventRow(previewLive, ZoneId.of("UTC"), isLive = true, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CompletedEventRowPreview() {
    LolSitoTheme { EventRow(previewCompleted, ZoneId.of("UTC"), isLive = false, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 320)
@Composable
private fun LongCodesEventRowPreview() {
    val long = previewCompleted.copy(
        match = previewCompleted.match!!.copy(
            teams = listOf(
                Team("Long Name", "LONGCODE", null, MatchOutcome.WIN, 2),
                Team("Other Long", "OTHERLONG", null, MatchOutcome.LOSS, 0),
            ),
        ),
    )
    LolSitoTheme { EventRow(long, ZoneId.of("UTC"), isLive = false, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScheduleNoSpoilersPreview() = PreviewContent(previewState.copy(noSpoilers = true))

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScheduleFavoritesPreview() = PreviewContent(
    previewState.copy(favoriteNames = listOf("T1"), favoritesOnly = true, resultDays = emptyList(), liveEvents = emptyList()),
)
