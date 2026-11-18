package com.example.lolsito.feature.standings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
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
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.ui.AppTopBar
import com.example.lolsito.core.ui.ChevronDownIcon
import com.example.lolsito.core.ui.DropdownPicker
import com.example.lolsito.core.ui.GroupLabel
import com.example.lolsito.core.ui.InfoText
import com.example.lolsito.core.ui.InlineError
import com.example.lolsito.core.ui.LiveBadge
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.MatchCard
import com.example.lolsito.core.ui.PickerRow
import com.example.lolsito.core.ui.PickerSheet
import com.example.lolsito.core.ui.SectionTitle
import com.example.lolsito.core.ui.Shapes
import com.example.lolsito.core.ui.SkeletonBlock
import com.example.lolsito.core.ui.SkeletonScreen
import com.example.lolsito.core.ui.Spacing
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.schedule.ui.StarIcon
import com.example.lolsito.feature.schedule.ui.TeamCell
import com.example.lolsito.feature.schedule.ui.dayRows
import com.example.lolsito.feature.standings.domain.StageTab
import com.example.lolsito.feature.standings.domain.TeamOption
import com.example.lolsito.feature.standings.domain.identity
import com.example.lolsito.feature.standings.domain.stageTabs
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import com.example.lolsito.ui.theme.LabelText
import com.example.lolsito.ui.theme.LolSitoTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Container: wires the ViewModel to the stateless [StandingsContent]. */
@Composable
fun StandingsScreen(
    viewModel: StandingsViewModel,
    onMatchClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the tournament was opened on top of another screen: a top bar with a back arrow is shown. */
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The mode is toggled in Schedule and favorites are starred in a team's profile: read both again whenever this tab comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNoSpoilers()
        viewModel.refreshFavorites()
    }
    StandingsContent(
        state = state,
        onSelectLeague = viewModel::selectLeague,
        onSelectTournament = viewModel::selectTournament,
        onRetryLeagues = viewModel::retryLeagues,
        onRetryTournaments = viewModel::retryTournaments,
        onRetryStandings = viewModel::retryStandings,
        onMatchClick = onMatchClick,
        onTeamClick = onTeamClick,
        modifier = modifier,
        onRefresh = viewModel::refresh,
        onSelectStage = viewModel::selectStage,
        onSelectStageView = viewModel::selectStageView,
        onRetryMatches = viewModel::retryMatches,
        onSelectTeamFilter = viewModel::selectTeamFilter,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsContent(
    state: StandingsUiState,
    onSelectLeague: (String) -> Unit,
    onSelectTournament: (String) -> Unit,
    onRetryLeagues: () -> Unit,
    onRetryTournaments: () -> Unit,
    onRetryStandings: () -> Unit,
    onMatchClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Pull to refresh on the standings list. */
    onRefresh: () -> Unit = {},
    onSelectStage: (String) -> Unit = {},
    onSelectStageView: (StageView) -> Unit = {},
    onRetryMatches: () -> Unit = {},
    onSelectTeamFilter: (Team?) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        if (onBack != null) AppTopBar(state.selectedTournament?.label ?: "Tournament", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.CardGap),
        ) {
            // Always shown: without leagues it still names the current selection, with no options.
            DropdownPicker(
                label = state.selectedLeagueLabel,
                items = state.leagues,
                itemLabel = { it.name },
                onSelect = { onSelectLeague(it.id) },
                modifier = Modifier.weight(1f),
                imageUrl = state.selectedLeague?.imageUrl,
                itemImageUrl = { it.imageUrl },
                title = "League",
                isSelected = { it.id == state.selectedLeagueId },
            )
            if (state.tournaments.isNotEmpty()) {
                DropdownPicker(
                    label = state.selectedTournament?.label ?: "Tournament",
                    items = state.tournaments,
                    itemLabel = { it.label },
                    onSelect = { onSelectTournament(it.id) },
                    modifier = Modifier.weight(1f),
                    title = "Tournament",
                    isSelected = { it.id == state.selectedTournamentId },
                )
            }
        }
        // Inline, picker-only error: the rest of the tab keeps working without the leagues.
        if (state.leaguesStatus is LoadStatus.Error) {
            InlineError("Couldn't load leagues", onRetryLeagues)
        }
        LoadStatusView(
            state.tournamentsStatus,
            "No tournaments for this league",
            onRetryTournaments,
            loading = { StandingsSkeleton() },
        ) {
            LoadStatusView(
                state.standingsStatus,
                "No standings for this tournament yet",
                onRetryStandings,
                loading = { StandingsSkeleton() },
            ) {
                val showsSelector = state.stageTabs.size > 1
                Column(Modifier.fillMaxSize()) {
                    if (showsSelector) StageSelector(state.stageTabs, state.selectedStageKey, onSelectStage)
                    if (state.canShowMatches) StageViewToggle(state.stageView, onSelectStageView)
                    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        if (state.canShowMatches && state.stageView == StageView.MATCHES) {
                            StageMatchesList(state, onMatchClick, onSelectTeamFilter, onRetryMatches)
                        } else {
                            StandingsList(
                                state.rows,
                                onMatchClick,
                                onTeamClick,
                                noSpoilers = state.noSpoilers,
                                favoriteNames = state.favoriteNames,
                                // The selector already names the stage.
                                showStageHeaders = !showsSelector,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Loading state shaped like a standings table: a stage title and the rows of its table. */
@Composable
private fun StandingsSkeleton(modifier: Modifier = Modifier, rows: Int = 8) {
    SkeletonScreen(modifier) {
        Column {
            SkeletonBlock(
                Modifier.padding(start = Spacing.Screen, top = 16.dp, bottom = 8.dp).size(width = 140.dp, height = 20.dp),
            )
            repeat(rows) {
                SkeletonBlock(Modifier.padding(horizontal = Spacing.Screen, vertical = 2.dp).fillMaxWidth().height(44.dp))
            }
        }
    }
}

@Composable
fun StandingsList(
    rows: List<StandingsRow>,
    onMatchClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
    /** Names of the starred teams: their table rows and match cards show a star. */
    favoriteNames: List<String> = emptyList(),
    showStageHeaders: Boolean = true,
) {
    val shown = if (showStageHeaders) rows else rows.filterNot { it is StandingsRow.StageHeader }
    LazyColumn(modifier.fillMaxSize(), contentPadding = ListEndPadding) {
        itemsIndexed(shown, key = { _, row -> row.key }) { index, row ->
            when (row) {
                is StandingsRow.StageHeader -> SectionTitle(row.name)
                is StandingsRow.SectionHeader -> GroupLabel(row.name, color = MaterialTheme.colorScheme.inversePrimary)
                is StandingsRow.TableHeader -> TableHeader()
                is StandingsRow.RankingRow -> RankingRow(
                    row,
                    onTeamClick,
                    favorite = row.isFavorite(favoriteNames),
                    // The table ends where the next row is not another ranking.
                    last = shown.getOrNull(index + 1) !is StandingsRow.RankingRow,
                )
                is StandingsRow.EmptySection -> InfoText("No standings for this section yet")
                is StandingsRow.MatchRow -> SectionMatchRow(
                    row,
                    onClick = { onMatchClick(row.sectionMatch.match.id) },
                    noSpoilers = noSpoilers,
                    favoriteNames = favoriteNames,
                )
                is StandingsRow.BracketRow -> BracketView(row, onMatchClick, noSpoilers = noSpoilers, favoriteNames = favoriteNames)
                is StandingsRow.SwissRow -> SwissView(row, onMatchClick, noSpoilers = noSpoilers, favoriteNames = favoriteNames)
            }
        }
    }
}

/**
 * One chip per stage, named as the API names it, in a row that scrolls sideways so no name is ever cut. Chips
 * keep it apart from the Table | Matches segmented toggle below it.
 */
@Composable
private fun StageSelector(tabs: List<StageTab>, selectedKey: String?, onSelect: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Screen, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.key == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(tab.key) },
                label = { Text(tab.name, maxLines = 1, softWrap = false) },
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, selectedBorderColor = accent),
            )
        }
    }
}

/** Table | Matches of a ranked stage, the same segmented buttons as the Items/Stats toggle of a game. */
@Composable
private fun StageViewToggle(view: StageView, onViewChange: (StageView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp)) {
        StageView.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == view,
                onClick = { onViewChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, StageView.entries.size),
                label = { Text(option.label) },
            )
        }
    }
}

/**
 * The matches of a ranked stage by day, with Schedule's cards: tapping one opens it, no-spoilers and favorite
 * stars apply as in Schedule, and a match being played shows LIVE. Above them, the team filter chip and the
 * chosen team's place and record in the stage.
 */
@Composable
private fun StageMatchesList(
    state: StandingsUiState,
    onMatchClick: (String) -> Unit,
    onSelectTeamFilter: (Team?) -> Unit,
    onRetry: () -> Unit,
) {
    LoadStatusView(state.matchesStatus, "No matches for this stage", onRetry) {
        var sheetOpen by remember { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListEndPadding) {
            item(key = "team-filter") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamFilterChip(state.teamFilter, onClick = { sheetOpen = true })
                    state.teamFilterStanding?.let { line ->
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = LabelText.Number, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            val filter = state.teamFilter
            if (filter != null && state.matchDays.isEmpty()) {
                item(key = "team-filter-empty") { InfoText("No matches for ${filter.code} in this stage") }
            }
            dayRows(
                state.matchDays,
                state.zoneId,
                state.noSpoilers,
                state.favoriteNames,
                onMatchClick,
                isLive = { it.state == MatchState.IN_PROGRESS },
            )
        }
        if (sheetOpen) {
            TeamFilterSheet(state.teamOptions, state.teamFilter, onSelectTeamFilter, onDismiss = { sheetOpen = false })
        }
    }
}

/** "All teams ▾", or the chosen team's logo and code. */
@Composable
private fun TeamFilterChip(team: Team?, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = team != null,
        onClick = onClick,
        label = { Text(team?.code ?: "All teams", maxLines = 1, softWrap = false) },
        // Logos are not tinted: they are often white on transparent, which reads on the black chip.
        leadingIcon = team?.let { { TeamLogo(it.imageUrl, contentDescription = null, size = 18.dp) } },
        trailingIcon = { Icon(ChevronDownIcon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = team != null, selectedBorderColor = accent),
    )
}

/** "All teams" and the stage's teams (favorites first, starred); picking one closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamFilterSheet(options: List<TeamOption>, selected: Team?, onSelect: (Team?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    fun pick(team: Team?) {
        onSelect(team)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    PickerSheet(title = "Team", onDismiss = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            item(key = "all-teams") { PickerRow("All teams", imageUrl = null, selected = selected == null, onClick = { pick(null) }) }
            items(options, key = { "team:${it.team.identity}" }) { option ->
                PickerRow(
                    label = option.team.name,
                    imageUrl = option.team.imageUrl,
                    selected = selected?.identity == option.team.identity,
                    onClick = { pick(option.team) },
                    badge = if (option.favorite) ({ FavoriteStar() }) else null,
                )
            }
        }
    }
}

/** The top of a ranking table: the column names on the card surface its [RankingRow]s continue. */
@Composable
private fun TableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen)
            .background(MaterialTheme.colorScheme.surfaceContainer, TABLE_TOP_SHAPE)
            .padding(start = Spacing.CardPadding, end = Spacing.CardPadding, top = 10.dp, bottom = 4.dp),
    ) {
        val style = LabelText.Overline
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text("#", style = style, color = color, modifier = Modifier.width(POSITION_WIDTH))
        Text("TEAM", style = style, color = color, modifier = Modifier.weight(1f))
        Text("W-L", style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(RECORD_WIDTH))
    }
}

/**
 * One row of a ranking table, on the same surface as the header above it; [last] rounds its bottom
 * corners to close the table. Opens the team profile on tap, unless the team has nothing to open: see
 * [StandingsRow.RankingRow.teamId]. [favorite] draws the star next to the team's code.
 */
@Composable
fun RankingRow(
    row: StandingsRow.RankingRow,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    last: Boolean = false,
) {
    val team = row.ranking.team
    val teamId = row.teamId
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen)
            .clip(if (last) TABLE_BOTTOM_SHAPE else RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (teamId != null) Modifier.clickable { onTeamClick(teamId) } else Modifier)
            .padding(start = Spacing.CardPadding, end = Spacing.CardPadding, top = 8.dp, bottom = if (last) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.position,
            style = LabelText.Number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(POSITION_WIDTH),
        )
        TeamLogo(team.imageUrl, contentDescription = team.name)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    team.code,
                    style = LabelText.TeamCode,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (favorite) {
                    Spacer(Modifier.width(4.dp))
                    FavoriteStar()
                }
            }
            Text(
                team.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(row.record, style = LabelText.Number, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(RECORD_WIDTH))
    }
}

/** The star Schedule draws next to a favorite team, at the same size and color. */
@Composable
internal fun FavoriteStar(modifier: Modifier = Modifier) {
    Icon(
        imageVector = StarIcon,
        contentDescription = "Favorite",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(12.dp),
    )
}

/**
 * Scoreboard card like Schedule's: first team, result, second team, with the LIVE badge under the result
 * of a match being played. Undecided (TBD) matches are not clickable: see [StandingsRow.MatchRow.isNavigable].
 */
@Composable
fun SectionMatchRow(
    row: StandingsRow.MatchRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    noSpoilers: Boolean = false,
    favoriteNames: List<String> = emptyList(),
) {
    val teams = row.sectionMatch.match.teams
    val result = row.displayedResult(noSpoilers)
    val score = result.score
    MatchCard(modifier, onClick = if (row.isNavigable) onClick else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (row.hasBothTeams) TeamCell(teams[0], result.emphasisFor(0), favorite = row.isFavorite(0, favoriteNames))
            }
            Column(
                Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    !row.hasBothTeams -> Text("TBD", style = LabelText.Score)
                    score != null -> Text(score, style = LabelText.Score)
                    else -> Text("vs", style = LabelText.Time, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (row.isLive) {
                    Spacer(Modifier.height(2.dp))
                    LiveBadge()
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (row.hasBothTeams) {
                    TeamCell(teams[1], result.emphasisFor(1), mirrored = true, favorite = row.isFavorite(1, favoriteNames))
                }
            }
        }
    }
}

private val POSITION_WIDTH = 32.dp
private val RECORD_WIDTH = 56.dp

/** A ranking table is one card cut into lazy rows: the header carries its top corners, the last row its bottom ones. */
private val TABLE_TOP_SHAPE = Shapes.Card.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize)
private val TABLE_BOTTOM_SHAPE = Shapes.Card.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)

private fun previewTeam(code: String, outcome: MatchOutcome? = null, wins: Int? = null) =
    Team(code, code, null, outcome, wins, id = code)

private val previewStandings = Standings(
    listOf(
        StandingsStage(
            "Groups", "groups",
            listOf(
                StandingsSection(
                    "Legend Group",
                    listOf(
                        Ranking(1, previewTeam("GEN"), 5, 3),
                        Ranking(2, previewTeam("HLE"), 4, 4),
                        Ranking(null, previewTeam("TBD"), null, null),
                    ),
                    emptyList(),
                ),
            ),
        ),
        StandingsStage(
            "Play-Ins", "play_ins",
            listOf(
                StandingsSection(
                    "Play-Ins",
                    emptyList(),
                    listOf(
                        SectionMatch(
                            MatchState.COMPLETED,
                            Match("1", listOf(previewTeam("BRO", MatchOutcome.LOSS, 2), previewTeam("KT", MatchOutcome.WIN, 3)), null),
                        ),
                    ),
                ),
            ),
        ),
        StandingsStage("Knockouts", "knockouts", emptyList()),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StandingsContentPreview() {
    LolSitoTheme {
        StandingsContent(
            state = StandingsUiState(
                leagues = listOf(League("1", "lck", "LCK", null, null)),
                leaguesStatus = LoadStatus.Content,
                selectedLeagueId = "1",
                tournamentsStatus = LoadStatus.Content,
                tournaments = listOf(Tournament("t", "lck_split_3_2026", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1))),
                selectedTournamentId = "t",
                standingsStatus = LoadStatus.Content,
                rows = buildStandingsRows(Standings(previewStandings.stages.take(1))),
                stageTabs = stageTabs(previewStandings),
                selectedStageKey = stageTabs(previewStandings).first().key,
                canShowMatches = true,
            ),
            onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {}, onTeamClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StandingsErrorPreview() {
    LolSitoTheme {
        StandingsContent(
            state = StandingsUiState(
                leagues = listOf(League("1", "lck", "LCK", null, null)),
                leaguesStatus = LoadStatus.Content,
                selectedLeagueId = "1",
                tournamentsStatus = LoadStatus.Error("Could not load tournaments"),
            ),
            onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {}, onTeamClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StandingsLeaguesErrorPreview() {
    LolSitoTheme {
        StandingsContent(
            state = StandingsUiState(
                leaguesStatus = LoadStatus.Error("Could not load leagues"),
                selectedLeagueId = "98767991310872058",
                tournamentsStatus = LoadStatus.Content,
                tournaments = listOf(Tournament("t", "lck_split_3_2026", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1))),
                selectedTournamentId = "t",
                standingsStatus = LoadStatus.Content,
                rows = buildStandingsRows(previewStandings),
            ),
            onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {}, onTeamClick = {},
        )
    }
}

/** Space after the last card of a list, so it does not run into the bottom bar. */
private val ListEndPadding = PaddingValues(bottom = Spacing.Screen)
