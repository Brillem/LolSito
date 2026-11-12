package com.example.lolsito.feature.teams.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.model.League
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.TeamLogo
import com.example.lolsito.feature.teams.domain.TeamSummary
import com.example.lolsito.ui.theme.LolSitoTheme

/** Container: wires the ViewModel to the stateless [TeamsContent]. [onTeamClick] receives the team id and the selected league id. */
@Composable
fun TeamsScreen(
    viewModel: TeamsViewModel,
    onTeamClick: (teamId: String, leagueId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TeamsContent(
        state = state,
        onSelectLeague = viewModel::selectLeague,
        onRetry = viewModel::retry,
        onRetryLeagues = viewModel::retryLeagues,
        onTeamClick = { teamId -> onTeamClick(teamId, state.selectedLeagueId) },
        modifier = modifier,
    )
}

@Composable
fun TeamsContent(
    state: TeamsUiState,
    onSelectLeague: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryLeagues: () -> Unit,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LeaguePicker(
            label = state.selectedLeagueName,
            leagues = state.leagues,
            onSelect = onSelectLeague,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (state.leaguesStatus is LoadStatus.Error) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Couldn't load leagues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetryLeagues) { Text("Retry") }
            }
        }
        LoadStatusView(state.status, "No teams for this league right now", onRetry) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.teams, key = { it.id }) { team -> TeamRow(team, onClick = { onTeamClick(team.id) }) }
            }
        }
    }
}

/** Dropdown of [leagues]; with none it only names the current selection and cannot be opened. */
@Composable
private fun LeaguePicker(label: String, leagues: List<League>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = leagues.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded && leagues.isNotEmpty(), onDismissRequest = { expanded = false }) {
            leagues.forEach { league ->
                DropdownMenuItem(
                    text = { Text(league.name) },
                    onClick = {
                        expanded = false
                        onSelect(league.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(team.imageUrl, contentDescription = null, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                team.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(team.code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TeamsContentPreview() {
    LolSitoTheme {
        TeamsContent(
            state = TeamsUiState(
                leagues = listOf(League("lck", "lck", "LCK", "KOREA", null)),
                leaguesStatus = LoadStatus.Content,
                selectedLeagueId = "lck",
                status = LoadStatus.Content,
                teams = listOf(TeamSummary("1", "Gen.G Esports", "GEN", null), TeamSummary("2", "T1", "T1", null)),
            ),
            onSelectLeague = {},
            onRetry = {},
            onRetryLeagues = {},
            onTeamClick = {},
        )
    }
}
