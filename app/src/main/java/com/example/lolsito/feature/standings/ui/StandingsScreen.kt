package com.example.lolsito.feature.standings.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.core.ui.ErrorView
import com.example.lolsito.core.ui.LoadingView
import com.example.lolsito.core.ui.MessageView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.standings.domain.Ranking
import com.example.lolsito.feature.standings.domain.SectionMatch
import com.example.lolsito.feature.standings.domain.Standings
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.StandingsStage
import com.example.lolsito.feature.standings.domain.Tournament
import java.time.LocalDate

/** Container: wires the ViewModel to the stateless [StandingsContent]. */
@Composable
fun StandingsScreen(
    viewModel: StandingsViewModel,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StandingsContent(
        state = state,
        onSelectLeague = viewModel::selectLeague,
        onSelectTournament = viewModel::selectTournament,
        onRetryLeagues = viewModel::retryLeagues,
        onRetryTournaments = viewModel::retryTournaments,
        onRetryStandings = viewModel::retryStandings,
        onMatchClick = onMatchClick,
        modifier = modifier,
    )
}

@Composable
fun StandingsContent(
    state: StandingsUiState,
    onSelectLeague: (String) -> Unit,
    onSelectTournament: (String) -> Unit,
    onRetryLeagues: () -> Unit,
    onRetryTournaments: () -> Unit,
    onRetryStandings: () -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Always shown: without leagues it still names the current selection, with no options.
            Picker(
                label = state.selectedLeagueLabel,
                items = state.leagues,
                itemLabel = { it.name },
                onSelect = { onSelectLeague(it.id) },
                modifier = Modifier.weight(1f),
            )
            if (state.tournaments.isNotEmpty()) {
                Picker(
                    label = state.selectedTournament?.label ?: "Tournament",
                    items = state.tournaments,
                    itemLabel = { t -> listOfNotNull(t.label, t.dateRange).joinToString("\n") },
                    onSelect = { onSelectTournament(it.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.leaguesStatus is LoadStatus.Error) {
            LeaguesError(onRetryLeagues)
        }
        when (val status = state.tournamentsStatus) {
            LoadStatus.Loading -> LoadingView()
            is LoadStatus.Error -> ErrorView(status.message, onRetryTournaments)
            LoadStatus.Empty -> MessageView("No tournaments for this league")
            LoadStatus.Content -> when (val standings = state.standingsStatus) {
                LoadStatus.Loading -> LoadingView()
                is LoadStatus.Error -> ErrorView(standings.message, onRetryStandings)
                LoadStatus.Empty -> MessageView("No standings for this tournament yet")
                LoadStatus.Content -> StandingsList(state.rows, onMatchClick)
            }
        }
    }
}

/** Inline, picker-only error: the rest of the tab keeps working without the leagues. */
@Composable
private fun LeaguesError(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Couldn't load leagues",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Dropdown picker. [items] are shown in order; [label] is the current selection. With no [items]
 * it only shows the selection and cannot be opened.
 */
@Composable
private fun <T> Picker(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded && items.isNotEmpty(), onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
fun StandingsList(rows: List<StandingsRow>, onMatchClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is StandingsRow.StageHeader -> StageHeader(row.name)
                is StandingsRow.SectionHeader -> SectionHeader(row.name)
                is StandingsRow.TableHeader -> TableHeader()
                is StandingsRow.RankingRow -> RankingRow(row)
                is StandingsRow.EmptySection -> InfoText("No standings for this section yet")
                is StandingsRow.EliminationNotice -> EliminationNotice(row.hasMatches)
                is StandingsRow.MatchRow -> SectionMatchRow(row, onClick = { onMatchClick(row.sectionMatch.match.id) })
            }
        }
    }
}

@Composable
private fun StageHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text("#", style = style, color = color, modifier = Modifier.width(POSITION_WIDTH))
        Text("Team", style = style, color = color, modifier = Modifier.weight(1f))
        Text("W-L", style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(RECORD_WIDTH))
    }
}

@Composable
fun RankingRow(row: StandingsRow.RankingRow, modifier: Modifier = Modifier) {
    val team = row.ranking.team
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.position, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(POSITION_WIDTH))
        TeamLogo(team.imageUrl, contentDescription = team.name)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(team.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                team.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(row.record, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.width(RECORD_WIDTH))
    }
}

@Composable
private fun EliminationNotice(hasMatches: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Elimination stage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (hasMatches) "Bracket and Swiss stages have no standings table. Matches:"
            else "Bracket and Swiss stages have no standings table. Check the Schedule tab for its matches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionMatchRow(row: StandingsRow.MatchRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val teams = row.sectionMatch.match.teams
    val result = row.result
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (teams.size >= 2) {
            MatchTeam(teams[0], result.emphasisFor(0), Modifier.weight(1f), alignEnd = true)
            Text(
                text = result.score ?: "vs",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (result.score != null) FontWeight.Bold else null,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp),
            )
            MatchTeam(teams[1], result.emphasisFor(1), Modifier.weight(1f), alignEnd = false)
        } else {
            Text("TBD", style = MaterialTheme.typography.bodyMedium)
        }
        if (row.sectionMatch.state == MatchState.IN_PROGRESS) {
            Text("LIVE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MatchTeam(team: Team, emphasis: TeamEmphasis, modifier: Modifier, alignEnd: Boolean) {
    Row(
        modifier.alpha(if (emphasis == TeamEmphasis.LOSER) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        val code = @Composable {
            Text(
                team.code,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasis == TeamEmphasis.WINNER) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
        }
        val logo = @Composable { TeamLogo(team.imageUrl, contentDescription = team.name, size = 24.dp) }
        if (alignEnd) code() else logo()
        Spacer(Modifier.width(6.dp))
        if (alignEnd) logo() else code()
    }
}

/** "worlds_2026" -> "Worlds 2026"; falls back to the id when the slug is blank. */
val Tournament.label: String
    get() = slug.split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
        .ifBlank { id }

val Tournament.dateRange: String?
    get() {
        val start = startDate ?: return null
        val end = endDate ?: return null
        return "$start – $end"
    }

private val POSITION_WIDTH = 32.dp
private val RECORD_WIDTH = 48.dp

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

@Preview(showBackground = true)
@Composable
private fun StandingsContentPreview() {
    StandingsContent(
        state = StandingsUiState(
            leagues = listOf(League("1", "lck", "LCK", null, null)),
            leaguesStatus = LoadStatus.Content,
            selectedLeagueId = "1",
            tournamentsStatus = LoadStatus.Content,
            tournaments = listOf(Tournament("t", "lck_split_3_2026", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1))),
            selectedTournamentId = "t",
            standingsStatus = LoadStatus.Content,
            rows = buildStandingsRows(previewStandings),
        ),
        onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StandingsErrorPreview() {
    StandingsContent(
        state = StandingsUiState(
            leagues = listOf(League("1", "lck", "LCK", null, null)),
            leaguesStatus = LoadStatus.Content,
            selectedLeagueId = "1",
            tournamentsStatus = LoadStatus.Error("Could not load tournaments"),
        ),
        onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StandingsLeaguesErrorPreview() {
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
        onSelectLeague = {}, onSelectTournament = {}, onRetryLeagues = {}, onRetryTournaments = {}, onRetryStandings = {}, onMatchClick = {},
    )
}
