package com.example.lolsito.feature.match.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.example.lolsito.feature.match.domain.GameSide
import com.example.lolsito.feature.match.domain.GameState
import com.example.lolsito.feature.match.domain.MatchDetail
import com.example.lolsito.feature.match.domain.MatchGame

/** Container: wires the ViewModel to the stateless [MatchDetailContent]. */
@Composable
fun MatchDetailScreen(viewModel: MatchDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MatchDetailContent(state = state, onBack = onBack, onRetry = viewModel::retry, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailContent(
    state: MatchDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
            is MatchDetailUiState.Content -> MatchDetailBody(state.detail)
        }
    }
}

@Composable
private fun MatchDetailBody(detail: MatchDetail) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") { MatchHeader(detail) }
        item(key = "games-title") {
            Text(
                "Games",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val games = detail.visibleGames
        if (games.isEmpty()) {
            item(key = "no-games") {
                Text(
                    "No games yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        val keys = detail.gameKeys
        items(games.size, key = { keys[it] }) { index ->
            GameRow(games[index], detail.teams)
            HorizontalDivider()
        }
    }
}

@Composable
fun MatchHeader(detail: MatchDetail, modifier: Modifier = Modifier) {
    val result = detail.result
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
                HeaderTeam(teams[0], result.emphasisFor(0), Modifier.weight(1f))
                Text(
                    text = result.score ?: if (detail.state == MatchState.IN_PROGRESS) "LIVE" else "vs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.score == null && detail.state == MatchState.IN_PROGRESS) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HeaderTeam(teams[1], result.emphasisFor(1), Modifier.weight(1f))
            }
        } else {
            Text("TBD", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HeaderTeam(team: Team, emphasis: TeamEmphasis, modifier: Modifier = Modifier) {
    Column(
        modifier.alpha(if (emphasis == TeamEmphasis.LOSER) LOSER_ALPHA else 1f),
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
fun GameRow(game: MatchGame, teams: List<Team>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
        Text(
            game.state.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (game.state == GameState.IN_PROGRESS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Preview(showBackground = true)
@Composable
private fun MatchDetailContentPreview() {
    MatchDetailContent(state = MatchDetailUiState.Content(previewDetail), onBack = {}, onRetry = {})
}

@Preview(showBackground = true)
@Composable
private fun MatchDetailErrorPreview() {
    MatchDetailContent(state = MatchDetailUiState.Error("Could not load the match"), onBack = {}, onRetry = {})
}
