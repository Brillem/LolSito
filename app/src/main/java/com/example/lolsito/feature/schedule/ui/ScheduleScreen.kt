package com.example.lolsito.feature.schedule.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.League
import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.core.ui.LOSER_ALPHA
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.MessageView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import com.example.lolsito.feature.schedule.domain.liveScoreLabel
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.Instant
import java.time.LocalDate
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
    ScheduleContent(
        state = state,
        onToggleLeague = viewModel::toggleLeague,
        onToggleLiveOnly = viewModel::toggleLiveOnly,
        onRetry = viewModel::retry,
        onLoadOlder = viewModel::loadOlder,
        onLoadNewer = viewModel::loadNewer,
        onMatchClick = onMatchClick,
        modifier = modifier,
    )
}

@Composable
fun ScheduleContent(
    state: ScheduleUiState,
    onToggleLeague: (String) -> Unit,
    onToggleLiveOnly: () -> Unit,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onLoadNewer: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScheduleFilterRow(state.leagues, state.selectedLeagueIds, state.liveOnly, onToggleLeague, onToggleLiveOnly)
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

/** The LIVE toggle, always first and always there, followed by one chip per league once they loaded. */
@Composable
fun ScheduleFilterRow(
    leagues: List<League>,
    selectedIds: Set<String>,
    liveOnly: Boolean,
    onToggleLeague: (String) -> Unit,
    onToggleLiveOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item(key = "live-toggle") {
            FilterChip(
                selected = liveOnly,
                onClick = onToggleLiveOnly,
                label = { Text("LIVE", fontWeight = FontWeight.Bold) },
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
        }
        items(leagues, key = { it.id }) { league ->
            val selected = league.id in selectedIds
            FilterChip(
                selected = selected,
                onClick = { onToggleLeague(league.id) },
                label = { Text(league.name) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    selectedBorderColor = accent,
                ),
            )
        }
    }
}

/** Live matches of every league; an empty feed shows a message instead of an empty list. */
@Composable
private fun LiveOnlyList(state: ScheduleUiState, onMatchClick: (String) -> Unit) {
    if (state.liveEvents.isEmpty()) {
        MessageView("No live matches right now")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        liveRows(state, onMatchClick)
    }
}

private fun LazyListScope.liveRows(state: ScheduleUiState, onMatchClick: (String) -> Unit) {
    itemsIndexed(state.liveEvents, key = { index, _ -> state.liveKeys[index] }) { _, event ->
        EventRow(event, state.zoneId, isLive = true, onClick = event.matchId?.let { id -> { onMatchClick(id) } })
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
        if (state.liveEvents.isNotEmpty()) {
            item(key = "live-header") { SectionHeader("Live now", accent = true) }
            liveRows(state, onMatchClick)
        }
        if (showUpcoming) {
            item(key = "upcoming-header") { SectionHeader("Upcoming") }
            dayRows(state.upcomingDays, state.zoneId, onMatchClick)
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
            dayRows(state.resultDays, state.zoneId, onMatchClick)
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
private fun LazyListScope.dayRows(days: List<ScheduleDay>, zoneId: ZoneId, onMatchClick: (String) -> Unit) {
    days.forEach { day ->
        stickyHeader(key = day.headerKey) { DayHeader(day.date) }
        itemsIndexed(day.events, key = { index, _ -> day.keys[index] }) { _, event ->
            // Live matches have their own section, so a row here is never live, whatever its state says.
            EventRow(event, zoneId, isLive = false, onClick = event.matchId?.let { id -> { onMatchClick(id) } })
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
    Text(
        text = date.format(dayFormatter),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Title of one of the three sections; the red one marks what is happening now. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (accent) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Scoreboard row: first team, result, second team, with the caption centered below. The result is the
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
    /** Null for rows without a match, which are not clickable. */
    onClick: (() -> Unit)? = null,
) {
    val teams = event.match?.teams.orEmpty()
    val hasTeams = teams.size >= 2
    val result = formatMatchResult(event.state, teams, event.match?.format)
    val time = event.startTime.atZone(zoneId).format(timeFormatter)
    val score = if (isLive) liveScoreLabel(teams) ?: "vs" else result.score
    // With a score (or live badge) in the middle the time has no place there: a finished match keeps it in the caption.
    val timeInCaption = !isLive && (score != null || !hasTeams)
    val caption = listOfNotNull(
        time.takeIf { timeInCaption },
        event.leagueName.takeIf { it.isNotBlank() },
        event.blockName,
        result.formatLabel,
    ).joinToString(" · ")
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (hasTeams) TeamCell(teams[0], result.emphasisFor(0))
                }
                Column(
                    Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        !hasTeams -> Text("TBD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        score != null -> Text(score, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        else -> Text(time, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (isLive) {
                        Spacer(Modifier.height(2.dp))
                        LiveBadge()
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (hasTeams) TeamCell(teams[1], result.emphasisFor(1), mirrored = true)
                }
            }
            if (caption.isNotEmpty()) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Logo and code; [mirrored] puts the code first, so the two teams of a row mirror each other around the result. */
@Composable
internal fun TeamCell(team: Team, emphasis: TeamEmphasis = TeamEmphasis.NEUTRAL, mirrored: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f),
    ) {
        val logo = @Composable { TeamLogo(team.imageUrl, contentDescription = team.name) }
        val code = @Composable {
            Text(
                team.code,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (mirrored) {
            code()
            Spacer(Modifier.width(6.dp))
            logo()
        } else {
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
            onToggleLeague = {}, onToggleLiveOnly = {}, onRetry = {}, onLoadOlder = {}, onLoadNewer = {}, onMatchClick = {},
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
