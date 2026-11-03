package com.example.lolsito.feature.schedule.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.schedule.domain.ScheduleDay
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
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
    ScheduleContent(
        state = state,
        onToggleLeague = viewModel::toggleLeague,
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
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onLoadNewer: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (state.leagues.isNotEmpty()) {
            LeagueFilterRow(state.leagues, state.selectedLeagueIds, onToggleLeague)
        }
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

@Composable
fun LeagueFilterRow(
    leagues: List<League>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(leagues, key = { it.id }) { league ->
            FilterChip(
                selected = league.id in selectedIds,
                onClick = { onToggle(league.id) },
                label = { Text(league.name) },
            )
        }
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
    LazyColumn(Modifier.fillMaxSize()) {
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
        if (state.days.isEmpty()) {
            item(key = "empty-range") {
                Text(
                    "No matches in this range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        state.days.forEach { day ->
            stickyHeader(key = "day-${day.date}") { DayHeader(day.date) }
            itemsIndexed(day.events, key = { index, _ -> day.keys[index] }) { _, event ->
                EventRow(event, state.zoneId, onClick = event.matchId?.let { id -> { onMatchClick(id) } })
            }
        }
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
}

/** Hidden when there is no cursor; disabled while any page loads, since only one loads at a time. */
@Composable
private fun PagingButton(label: String, available: Boolean, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    if (!available && !loading) return
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator() else TextButton(onClick = onClick, enabled = enabled) { Text(label) }
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
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun EventRow(
    event: ScheduleEvent,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    /** Null for rows without a match, which are not clickable. */
    onClick: (() -> Unit)? = null,
) {
    val teams = event.match?.teams.orEmpty()
    val result = formatMatchResult(event.state, teams, event.match?.format)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(event.startTime.atZone(zoneId).format(timeFormatter), style = MaterialTheme.typography.bodyMedium)
            if (event.state == MatchState.IN_PROGRESS) {
                Text("LIVE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (teams.size >= 2) {
                    TeamCell(teams[0], result.emphasisFor(0))
                    Text(
                        text = result.score?.let { " $it " } ?: " vs ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (result.score != null) FontWeight.Bold else null,
                    )
                    TeamCell(teams[1], result.emphasisFor(1))
                } else {
                    Text("TBD", style = MaterialTheme.typography.bodyMedium)
                }
            }
            val meta = listOfNotNull(
                event.leagueName.takeIf { it.isNotBlank() },
                event.blockName,
                result.formatLabel,
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TeamCell(team: Team, emphasis: TeamEmphasis = TeamEmphasis.NEUTRAL) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f),
    ) {
        TeamLogo(team.imageUrl, contentDescription = team.name)
        Spacer(Modifier.width(6.dp))
        Text(
            team.code,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}

private val previewEvent = ScheduleEvent(
    startTime = Instant.parse("2026-10-20T07:00:00Z"),
    state = MatchState.UNSTARTED,
    blockName = "Swiss",
    leagueName = "Worlds",
    leagueSlug = "worlds",
    match = Match(
        id = "1",
        teams = listOf(Team("T1", "T1", null, null, null), Team("Gen.G", "GEN", null, null, null)),
        format = SeriesFormat(SeriesType.BEST_OF, 3),
    ),
)

@Preview(showBackground = true)
@Composable
private fun ScheduleContentPreview() {
    ScheduleContent(
        state = ScheduleUiState(
            leagues = listOf(League("1", "worlds", "Worlds", null, null), League("2", "lck", "LCK", null, null)),
            selectedLeagueIds = setOf("1"),
            status = LoadStatus.Content,
            days = listOf(ScheduleDay(LocalDate.of(2026, 10, 20), listOf(previewEvent))),
            canLoadOlder = true,
            canLoadNewer = true,
            zoneId = ZoneId.of("UTC"),
        ),
        onToggleLeague = {}, onRetry = {}, onLoadOlder = {}, onLoadNewer = {}, onMatchClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun EventRowPreview() {
    EventRow(previewEvent, ZoneId.of("UTC"), onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun CompletedEventRowPreview() {
    EventRow(
        previewEvent.copy(
            state = MatchState.COMPLETED,
            match = previewEvent.match!!.copy(
                teams = listOf(
                    Team("T1", "T1", null, MatchOutcome.LOSS, 1),
                    Team("Gen.G", "GEN", null, MatchOutcome.WIN, 2),
                ),
            ),
        ),
        ZoneId.of("UTC"),
        onClick = {},
    )
}
