package com.example.lolsito.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lolsito.core.ui.LoadStatus
import com.example.lolsito.core.ui.LoadStatusView
import com.example.lolsito.core.ui.WhileStarted
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats

/** Container: wires the ViewModel to the stateless [GameStatsContent]. */
@Composable
fun GameStatsScreen(
    viewModel: GameStatsViewModel,
    blueTeamCode: String?,
    redTeamCode: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhileStarted(viewModel) { viewModel.pollWhileVisible() }
    GameStatsContent(
        state = state,
        blueTeamCode = blueTeamCode,
        redTeamCode = redTeamCode,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatsContent(
    state: GameStatsUiState,
    /** Codes of the teams on each side; the side name is shown when a code is unknown. */
    blueTeamCode: String?,
    redTeamCode: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Game stats") },
            // The app Scaffold already applies the system bar insets.
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
            },
        )
        LoadStatusView(state.status, emptyMessage = "No stats available for this game", onRetry = onRetry) {
            state.stats?.let { GameStatsBody(it, blueTeamCode, redTeamCode) }
        }
    }
}

/** The team [code] when known, otherwise [fallback] (the side). */
fun teamLabel(code: String?, fallback: String): String = code?.takeIf { it.isNotBlank() } ?: fallback

/** Gold as broadcasts show it: from a thousand up, in thousands rounded to one decimal ("31.9k"). */
fun formatGold(gold: Int): String {
    if (gold < 1000) return gold.toString()
    val tenths = (gold + 50) / 100
    return "${tenths / 10}.${tenths % 10}k"
}

private enum class Side(val label: String, val color: Color) {
    BLUE("Blue side", Color(0xFF2F80ED)),
    RED("Red side", Color(0xFFE5484D)),
}

private val teamRows: List<Pair<String, (TeamStats) -> String>> = listOf(
    "Gold" to { team -> formatGold(team.totalGold) },
    "Kills" to { team -> team.kills.toString() },
    "Towers" to { team -> team.towers.toString() },
    "Dragons" to { team -> team.dragons.toString() },
    "Barons" to { team -> team.barons.toString() },
)

@Composable
private fun GameStatsBody(stats: GameStats, blueTeamCode: String?, redTeamCode: String?) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "teams") {
            TeamComparison(stats, blueLabel = teamLabel(blueTeamCode, "Blue"), redLabel = teamLabel(redTeamCode, "Red"))
        }
        players(Side.BLUE, teamLabel(blueTeamCode, Side.BLUE.label), stats.blueTeam)
        players(Side.RED, teamLabel(redTeamCode, Side.RED.label), stats.redTeam)
    }
}

@Composable
private fun TeamComparison(stats: GameStats, blueLabel: String, redLabel: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComparisonRow(
            blue = blueLabel,
            label = if (stats.isFinished) "Final" else "LIVE",
            red = redLabel,
            header = true,
        )
        teamRows.forEach { (label, value) ->
            ComparisonRow(blue = value(stats.blueTeam), label = label, red = value(stats.redTeam))
        }
    }
}

@Composable
private fun ComparisonRow(blue: String, label: String, red: String, header: Boolean = false) {
    val valueStyle = if (header) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            blue,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = if (header) Side.BLUE.color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            red,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = if (header) Side.RED.color else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun LazyListScope.players(side: Side, title: String, team: TeamStats) {
    item(key = "${side.name}-title") {
        Column {
            HorizontalDivider()
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = side.color,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            PlayerColumns(
                name = "Player",
                kda = "K/D/A",
                cs = "CS",
                level = "Lvl",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Indented past the side strip of the player rows.
                modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
    val keys = team.playerKeys
    items(team.players.size, key = { "${side.name}:${keys[it]}" }) { index ->
        PlayerRow(team.players[index], side)
    }
}

@Composable
private fun PlayerRow(player: PlayerStats, side: Side) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(side.color))
        Spacer(Modifier.width(8.dp))
        PlayerColumns(
            name = player.summonerName,
            champion = player.championId,
            kda = "${player.kills}/${player.deaths}/${player.assists}",
            cs = player.creepScore.toString(),
            level = player.level.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One player line, shared with the column headers so values line up under their labels. */
@Composable
private fun PlayerColumns(
    name: String,
    kda: String,
    cs: String,
    level: String,
    color: Color,
    modifier: Modifier = Modifier,
    champion: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (champion != null) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (champion != null) {
                Text(
                    champion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(kda, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        Text(cs, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
        Text(level, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

private fun previewTeam(firstId: Int, prefix: String) = TeamStats(
    totalGold = 31850,
    kills = 2,
    towers = 1,
    dragons = 1,
    barons = 0,
    players = (firstId until firstId + 5).map { id ->
        PlayerStats(id, "$prefix Player$id", "Olaf", kills = 1, deaths = 2, assists = 3, creepScore = 142, level = 12)
    },
)

@Preview(showBackground = true)
@Composable
private fun GameStatsContentPreview() {
    GameStatsContent(
        state = GameStatsUiState(
            LoadStatus.Content,
            GameStats(isFinished = true, blueTeam = previewTeam(1, "MVK"), redTeam = previewTeam(6, "FUR")),
        ),
        blueTeamCode = "MVK",
        redTeamCode = "FUR",
        onBack = {},
        onRetry = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun GameStatsEmptyPreview() {
    GameStatsContent(
        state = GameStatsUiState(LoadStatus.Empty),
        blueTeamCode = null,
        redTeamCode = null,
        onBack = {},
        onRetry = {},
    )
}
